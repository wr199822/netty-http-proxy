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
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpGet;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;


@Slf4j
@Component
public class HttpProxyClientHandle extends ChannelInboundHandlerAdapter {

    public  String targetIp;

    public  String targetPort;

    public  String rewriteHost;

    private Channel serverCh;

    private ServerChannelEnum targetChannelState = ServerChannelEnum.INIT;

    Promise<Object> promise = null;




    private Queue<FullHttpRequest> pendingRequestQueue = new LinkedList<>();

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
        promise = new DefaultPromise<>(ctx.executor());
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
            serverCh.pipeline().fireUserEventTriggered(new ClientChannelAttachEvent(ctx.channel()));
            if (pendingRequestQueue.peek() == null) {
                //说明是第一次连接
                targetChannelState = ServerChannelEnum.READY;
                return;
            }
            //说明是重新连接
            authenticationDoneWriting(ctx,reduceQueue());
            targetChannelState = ServerChannelEnum.PENDING;
        });
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        FullHttpRequest request = (FullHttpRequest) msg;
        switch (targetChannelState) {
            case DISCONNECT:
                targetChannelState = ServerChannelEnum.CONNECTING;  //防止有多条消息 但是客户端正在连接
                addQueue(ctx, request);
                connectServer(ctx); //向后执行 保存这次的消息到queue中
                break;
            case CONNECTING:
            case PENDING:
                addQueue(ctx, request);
                break;
            case READY:
                authenticationDoneWriting(ctx,request);
                targetChannelState = ServerChannelEnum.PENDING;
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
            if (pendingRequestQueue.peek()==null){
                //说明队列中处理完毕
                targetChannelState = ServerChannelEnum.READY;
                return;
            }
            authenticationDoneWriting(ctx,reduceQueue());
            targetChannelState = ServerChannelEnum.PENDING;


        }
    }

    private void authenticationDoneWriting(ChannelHandlerContext ctx,FullHttpRequest request){
        authenticationTaskSubmit(ctx,request);
        promise.addListeners(future -> {
            if (promise.isSuccess()){
                serverCh.writeAndFlush(request).addListener(FIRE_EXCEPTION_ON_FAILURE);
            }else{
                abnormalWrite(ctx,HttpResponseStatus.UNAUTHORIZED,"Authorization失败");
            }
        });


    }

    private void authenticationTaskSubmit(ChannelHandlerContext ctx,FullHttpRequest request){
        try {

            //这里的线程调度是  netty中的eventLoop的线程执行完read之后把任务一提交 相当于这个消息以及被他消费了，
            //然后就是我们自己的线程池中的线程来消费这个剩下的步骤，因为这个channel并没有关闭，我们利用channel来执行剩下的步骤就行了
            HttpProxyConst.threadPoolExecutor.submit(() -> {
                if (!ctx.channel().isActive()){
                    return;
                }
                request.headers().set("Host", rewriteHost);
                String authorization = request.headers().get("Authorization");
                if (authorization == null) {
                    abnormalWrite(ctx,HttpResponseStatus.UNAUTHORIZED,"Authorization头不能为空");
                    return;
                }
                try{
                    HttpGet httpGet = new HttpGet("http://121.4.47.125:880/bearer");
                    httpGet.setHeader(org.apache.http.HttpHeaders.ACCEPT, "application/json");
                    httpGet.setHeader(org.apache.http.HttpHeaders.AUTHORIZATION, authorization);
                    org.apache.http.HttpResponse response = HttpProxyConst.httpClient.execute(httpGet);
                    if (response.getStatusLine().getStatusCode() == 200) {
                        promise.setSuccess(null);
                    } else {
                        promise.setFailure(null);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            });
        } catch (RejectedExecutionException e) {
            abnormalWrite(ctx,HttpResponseStatus.SERVICE_UNAVAILABLE,"服务器负载过高");
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
        INIT,CONNECTING,READY,PENDING,DISCONNECT
    }



}