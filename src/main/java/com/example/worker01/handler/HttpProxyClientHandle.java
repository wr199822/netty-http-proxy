package com.example.worker01.handler;

import com.example.worker01.config.BootstrapManage;
import com.example.worker01.config.HttpProxyConst;
import com.example.worker01.entity.ClientChannelAttachEvent;
import com.example.worker01.entity.ClientReplyStatusTransitionEvent;
import com.example.worker01.entity.TargetChannelDisconnectEvent;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;


@Slf4j
@Component
public class HttpProxyClientHandle extends ChannelInboundHandlerAdapter {

    public  String targetIp;

    public  String targetPort;

    public  String rewriteHost;

    private Channel serverCh;

    private ServerChannelEnum targetChannelState = ServerChannelEnum.INIT;

    private volatile boolean replyStatus=true;

    private volatile boolean StartExcutorStatus=false;


    private final ConcurrentLinkedQueue<FullHttpRequest> pendingRequestQueue = new ConcurrentLinkedQueue<>();

    public HttpProxyClientHandle(String targetIp, String targetPort, String rewriteHost) {
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.rewriteHost = rewriteHost;
    }

    public HttpProxyClientHandle() {
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("read 客户端channel{}", ctx.channel());
        targetChannelState = ServerChannelEnum.CONNECTING;
        connectServer(ctx);
    }

    private void connectServer(ChannelHandlerContext ctx) {
        EventLoop eventLoop = ctx.channel().eventLoop();
        Bootstrap bootstrap = BootstrapManage.getBootstrap(eventLoop);
        ChannelFuture cf = bootstrap.connect(targetIp, Integer.parseInt(targetPort));
        cf.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                log.info("未连上服务器端，关闭客户端channel");
                ctx.channel().close();
                return;
            }
            serverCh = cf.channel();
            targetChannelState = ServerChannelEnum.READY;
            serverCh.pipeline().fireUserEventTriggered(new ClientChannelAttachEvent(ctx.channel()));
            submitTask(ctx);

        });
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        FullHttpRequest request = (FullHttpRequest) msg;

        switch (targetChannelState) {
            case DISCONNECT:
                targetChannelState = ServerChannelEnum.CONNECTING;  //防止有多条消息 但是客户端正在连接
                connectServer(ctx); //向后执行 保存这次的消息到queue中
            case CONNECTING:
                addQueue(ctx, request);
                break;
            case READY:
                if (pendingRequestQueue.peek() == null&& replyStatus){
                    authenticationDoneWriting(ctx,request);
                    return;
                }else{
                    addQueue(ctx,request);
                }
                if (!StartExcutorStatus){
                    submitTask(ctx);
                    StartExcutorStatus=true;
                }
                break;
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,
                                Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        releaseQueue();
        if (serverCh!=null){
            //如果在 连接中 serverCh被关闭就可能会有NPE异常
            serverCh.close();
        }
    }


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt == TargetChannelDisconnectEvent.getInstance()){
            serverCh = null;
            targetChannelState =  ServerChannelEnum.DISCONNECT;
        }else if (evt == ClientReplyStatusTransitionEvent.getInstance()){
            replyStatus = true;
        }
    }

    private void submitTask(final ChannelHandlerContext ctx){
        try {
            //这里的线程调度是  netty中的eventLoop的线程执行完read之后把任务一提交 相当于这个消息以及被他消费了，
            //然后就是我们自己的线程池中的线程来消费这个剩下的步骤，因为这个channel并没有关闭，我们利用channel来执行剩下的步骤就行了
            HttpProxyConst.threadPoolExecutor.submit(() -> {
                if (!ctx.channel().isActive()){
                    return;
                }
                while (pendingRequestQueue.peek() != null ) {
                    if (replyStatus){
                        FullHttpRequest request = reduceQueue();
                        authenticationDoneWriting(ctx,request);
                    }
                }
                StartExcutorStatus=false;
            });
        } catch (RejectedExecutionException e) {
            abnormalWrite(ctx,HttpResponseStatus.SERVICE_UNAVAILABLE,"服务器负载过高");
        }
    }

    private void authenticationDoneWriting(ChannelHandlerContext ctx,FullHttpRequest request){
        request.headers().set("Host", rewriteHost);
        String authorization = request.headers().get("Authorization");
        if (authorization == null) {
            abnormalWrite(ctx,HttpResponseStatus.UNAUTHORIZED,"Authorization头不能为空");
        }
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet("http://121.4.47.125:880/bearer");
            httpGet.setHeader(org.apache.http.HttpHeaders.ACCEPT, "application/json");
            httpGet.setHeader(org.apache.http.HttpHeaders.AUTHORIZATION, authorization);
            org.apache.http.HttpResponse response = httpClient.execute(httpGet);
            if (response.getStatusLine().getStatusCode() == 200) {
                serverCh.writeAndFlush(request).addListener((ChannelFutureListener) writeFuture -> {
                    Throwable cause = writeFuture.cause();
                    if (cause != null) {
                        writeFuture.cause().printStackTrace();
                    }
                });
                replyStatus = false;
            } else {
                abnormalWrite(ctx,HttpResponseStatus.UNAUTHORIZED,"Authorization失败");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //给客户端回写异常情况
    private void abnormalWrite(ChannelHandlerContext ctx, HttpResponseStatus httpCode, String message) {
        ctx.channel().writeAndFlush(
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                        httpCode,
                        Unpooled.wrappedBuffer(message.getBytes())))
                .addListener(ChannelFutureListener.CLOSE);
    }

    //消费消息
    private FullHttpRequest reduceQueue(){
        FullHttpRequest fullHttpRequest = pendingRequestQueue.poll();
        HttpProxyConst.reducePendingRequestQueueGlobalSize();
        return fullHttpRequest;
    }

    //添加消息
    private void addQueue(ChannelHandlerContext ctx,FullHttpRequest request){
        if (pendingRequestQueue.size() > 20 || HttpProxyConst.checkPendingRequestQueueGlobalSize()) {
            abnormalWrite(ctx,HttpResponseStatus.SERVICE_UNAVAILABLE,"消息堆积过多,服务端连接异常");
        }
        HttpProxyConst.addPendingRequestQueueGlobalSize();
        pendingRequestQueue.offer(request);
    }

    private void releaseQueue(){
        int size = pendingRequestQueue.size();
        try{
            for (int i = 0; i < size; i++) {
                FullHttpRequest poll = pendingRequestQueue.poll();
                assert poll != null;
                poll.content().release();
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            HttpProxyConst.reducePendingRequestQueueGlobalSize(size);
        }
    }


    private enum ServerChannelEnum{
        INIT,CONNECTING,READY,DISCONNECT
    }



}