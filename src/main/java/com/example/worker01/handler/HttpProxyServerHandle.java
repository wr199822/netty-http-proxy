package com.example.worker01.handler;

import com.example.worker01.client.HttpProxyClientInitializer;
import com.example.worker01.config.BootstrapManage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;


@Slf4j
public class HttpProxyServerHandle extends ChannelInboundHandlerAdapter {


    private String targetIp;

    private String targetPort;

    private String rewriteHost;

    private Channel serverCh;


    private ArrayBlockingQueue<FullHttpRequest> queue = new ArrayBlockingQueue<FullHttpRequest>(1024);

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
        EventLoop eventLoop = ctx.channel().eventLoop();
        Bootstrap bootstrap = BootstrapManage.getBootstrap(eventLoop);
        ChannelFuture cf = bootstrap.connect(targetIp, Integer.parseInt(targetPort));
        cf.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    serverCh = cf.channel();
                    ctx.channel().attr(BootstrapManage.SET_SERVER_CHANNEL).set("1");
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
        FullHttpRequest request = (FullHttpRequest) msg;
        request.headers().set("Host", rewriteHost);
        if (serverCh == null) {
            boolean offer = queue.offer(request);
            if (!offer) {
                ctx.channel().writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.wrappedBuffer("消息堆积过多,服务端连接异常".getBytes())));
            }
        } else {
            if (queue.peek() == null) {
                serverCh.writeAndFlush(request);
            } else {
                while (queue.peek() != null) {
                    serverCh.writeAndFlush(queue.poll());
                }
                serverCh.writeAndFlush(request);
            }
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
        super.channelInactive(ctx);
    }

    private void releaseQueue(){
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            FullHttpRequest poll = queue.poll();
            poll.content().release();
        }
    }

//    @Override
//    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//        if (evt instanceof IdleStateEvent) {
//            IdleStateEvent event = (IdleStateEvent) evt;
//            switch (event.state()) {
//                case READER_IDLE:
//                    ctx.channel().close(); // 手动断开连接
//                    serverCh.close();
//                    log.info("读超时，关闭两端连接成功");
//                    break;
//                case WRITER_IDLE:
//                    break; // 不处理
//                case ALL_IDLE:
//                    break; // 不处理
//            }
//        }else{
//            super.userEventTriggered(ctx, evt);
//        }
//    }



}