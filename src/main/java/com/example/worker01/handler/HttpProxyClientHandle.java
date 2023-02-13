package com.example.worker01.handler;

import com.example.worker01.config.BootstrapManage;
import com.example.worker01.config.HttpProxyConst;
import com.example.worker01.entity.ClientChannelAttachEvent;
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
import java.util.concurrent.Future;


@Slf4j
@Component
public class HttpProxyClientHandle extends ChannelInboundHandlerAdapter {

    public  String targetIp;

    public  String targetPort;

    public  String rewriteHost;

    private Channel serverCh;

    private ServerChannelEnum targetChannelState = ServerChannelEnum.INIT;

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
            while (pendingRequestQueue.peek() != null) {
                FullHttpRequest fullHttpRequest = reduceQueue();
                serverCh.writeAndFlush(fullHttpRequest)
                        .addListener((ChannelFutureListener) writeFuture -> {
                            Throwable cause = writeFuture.cause();
                            if (cause != null) {
                                writeFuture.cause().printStackTrace();
                            }
                        });
            }
        });
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        FullHttpRequest request = (FullHttpRequest) msg;
        request.headers().set("Host", rewriteHost);
        String authorization = request.headers().get("Authorization");
        if (authorization == null) {
            ctx.writeAndFlush(ctx.channel().writeAndFlush(
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                            HttpResponseStatus.UNAUTHORIZED,
                            Unpooled.wrappedBuffer("Authorization头不能为空".getBytes())))
                    .addListener(ChannelFutureListener.CLOSE));
        }
        HttpProxyConst.threadPoolExecutor.execute(() -> {
            try(CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet httpGet = new HttpGet("http://121.4.47.125:880/bearer");
                httpGet.setHeader(org.apache.http.HttpHeaders.ACCEPT, "application/json");
                httpGet.setHeader(org.apache.http.HttpHeaders.AUTHORIZATION, authorization);
                org.apache.http.HttpResponse response = httpClient.execute(httpGet);
                if (response.getStatusLine().getStatusCode()==200){
                    System.out.println("success");
                    switch (targetChannelState) {
                        case DISCONNECT:
                            targetChannelState = ServerChannelEnum.CONNECTING;  //防止有多条消息 但是客户端正在连接
                            connectServer(ctx); //向后执行 保存这次的消息到queue中
                        case CONNECTING:
                            addQueue(ctx,request);
                            break;
                        case READY:
                            serverCh.writeAndFlush(request);
                            break;
                    }
                }else {
                    System.out.println("fail");
                    Thread.sleep(1000);
                    ctx.writeAndFlush(ctx.channel().writeAndFlush(
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.UNAUTHORIZED,
                                    Unpooled.wrappedBuffer("Authorization失败".getBytes())))
                            .addListener(ChannelFutureListener.CLOSE));
                }
            }catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
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
        }
    }

    private FullHttpRequest reduceQueue(){
        FullHttpRequest fullHttpRequest = pendingRequestQueue.poll();
        HttpProxyConst.reducePendingRequestQueueGlobalSize();
        return fullHttpRequest;
    }

    private void addQueue(ChannelHandlerContext ctx,FullHttpRequest request){
        if (pendingRequestQueue.size() > 20 || HttpProxyConst.checkPendingRequestQueueGlobalSize()) {
            ctx.channel().writeAndFlush(
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                            HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            Unpooled.wrappedBuffer("消息堆积过多,服务端连接异常".getBytes())))
                    .addListener(ChannelFutureListener.CLOSE);
        }
        HttpProxyConst.addPendingRequestQueueGlobalSize();
        pendingRequestQueue.offer(request);
    }

    private void releaseQueue(){
        int size = pendingRequestQueue.size();
        try{
            for (int i = 0; i < size; i++) {
                FullHttpRequest poll = pendingRequestQueue.poll();
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