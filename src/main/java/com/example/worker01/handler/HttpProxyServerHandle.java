package com.example.worker01.handler;

import com.example.worker01.config.BootstrapManage;
import com.example.worker01.config.HttpProxyEvent;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.compiler.lir.util.ValueSet;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;


@Slf4j
public class HttpProxyServerHandle extends ChannelInboundHandlerAdapter {


    private String targetIp;

    private String targetPort;

    private String rewriteHost;

    private Channel serverCh;

//    private boolean serverConnectTag = false;  //false 不可以连接  true可以连接

    private ServerChannelEnum state = ServerChannelEnum.NOTCONNECT;


    private Queue<FullHttpRequest> queue = new LinkedList<>();

    public HttpProxyServerHandle(String targetIp, String targetPort, String rewriteHost) {
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.rewriteHost = rewriteHost;
    }

    public HttpProxyServerHandle() {
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("read 客户端channel{}", ctx.channel());
        connectServer(ctx);
    }

    private void connectServer(ChannelHandlerContext ctx){
        EventLoop eventLoop = ctx.channel().eventLoop();
        Bootstrap bootstrap = BootstrapManage.getBootstrap(eventLoop);
        ChannelFuture cf = bootstrap.connect(targetIp, Integer.parseInt(targetPort));
        cf.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    serverCh = cf.channel();
                    state = ServerChannelEnum.CONNECTING;
                    HttpProxyEvent httpProxyEvent = new HttpProxyEvent();
                    httpProxyEvent.setChannel(ctx.channel());
                    serverCh.pipeline().fireUserEventTriggered(ctx.channel());
                } else {
                    log.info("未连上服务器端，关闭客户端channel");
                    ctx.channel().close();
                }
            }
        });
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        switch (state){
            case CONNECTING:
                FullHttpRequest request = (FullHttpRequest) msg;
                request.headers().set("Host", rewriteHost);
                if (serverCh == null) {
                    queue.offer(request);
                    if (queue.size()>1024) {
                        ctx.channel().writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.wrappedBuffer("消息堆积过多,服务端连接异常".getBytes())));
                        ctx.close();
                    }
                } else {
                    if (queue.peek() == null) {
                        serverCh.writeAndFlush(request);
                    } else {
                        // 这里保证了 queue 并不会堆积
                        while (queue.peek() != null) {
                            serverCh.writeAndFlush(queue.poll());
                        }
                        serverCh.writeAndFlush(request);
                    }
                }
                break;
            case NOTCONNECT://正常不会有这种情况；
            case CONNECTED:connectServer(ctx);break;

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
        releaseQueue(ctx);
        serverCh.close();
    }

    private void releaseQueue(ChannelHandlerContext ctx){
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            FullHttpRequest poll = queue.poll();
            poll.content().release();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpProxyEvent && ((HttpProxyEvent) evt).getType().equals("1")){
            serverCh = null;
            state =  ServerChannelEnum.CONNECTED;
        }
    }


    private enum ServerChannelEnum{
        NOTCONNECT,CONNECTING,CONNECTED
    }



}