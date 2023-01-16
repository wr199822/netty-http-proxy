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
import java.util.LinkedList;
import java.util.Queue;


@Slf4j
public class HttpProxyClientHandle extends ChannelInboundHandlerAdapter {

    private String targetIp;

    private String targetPort;

    private String rewriteHost;

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
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("read 客户端channel{}", ctx.channel());
        targetChannelState = ServerChannelEnum.CONNECTING;
        connectServer(ctx);
    }

    private void connectServer(ChannelHandlerContext ctx){
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
            serverCh.pipeline().fireUserEventTriggered(new ClientChannelAttachEvent(ctx.channel(),pendingRequestQueue));
        });
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        FullHttpRequest request = (FullHttpRequest) msg;
        request.headers().set("Host", rewriteHost);
        switch (targetChannelState){
            case DISCONNECT:
                targetChannelState = ServerChannelEnum.CONNECTING;  //防止有多条消息 但是客户端正在连接
                connectServer(ctx); //向后执行 保存这次的消息到queue中
            case CONNECTING:
                pendingRequestQueue.offer(request);
                HttpProxyConst.addPendingRequestQueueGlobalSize();
                //全局queueSize如何 控制。
                if (pendingRequestQueue.size()>20&&HttpProxyConst.checkPendingRequestQueueGlobalSize()) {
                    ctx.channel().writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.wrappedBuffer("消息堆积过多,服务端连接异常".getBytes())));
                    ctx.close();
                }
                break;
            case READY:
                HttpProxyConst.reducePendingRequestQueueGlobalSize();
                serverCh.writeAndFlush(request);
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
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        releaseQueue();
        if (serverCh!=null){
            //如果在 连接中 serverCh被关闭就可能会有NPE异常
            serverCh.close();
        }
    }

    private void releaseQueue(){
        int size = pendingRequestQueue.size();
        for (int i = 0; i < size; i++) {
            FullHttpRequest poll = pendingRequestQueue.poll();
            poll.content().release();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof TargetChannelDisconnectEvent){
            serverCh = null;
            targetChannelState =  ServerChannelEnum.DISCONNECT;
        }
    }


    private enum ServerChannelEnum{
        INIT,CONNECTING,READY,DISCONNECT
    }



}