package com.example.worker01.handler;

import com.example.worker01.client.HttpProxyClientInitializer;
import com.example.worker01.config.BootstrapManage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TransferQueue;


@Slf4j
public class HttpProxyServerHandle extends ChannelInboundHandlerAdapter {


    private String targetIp;

    private String targetPort;

    private String rewriteHost;

    private int readIdleTimes;

    private Channel ch;
    private long start;
    private long end;

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
        start = System.currentTimeMillis();
        InetSocketAddress insocket = (InetSocketAddress)ctx.channel().remoteAddress();
        String ip = String.valueOf(insocket.getAddress());
        //连接至目标服务器
        Bootstrap bootstrap ;
        if (BootstrapManage.bootstrapMap.get(ctx.channel().eventLoop())==null){
            bootstrap = new Bootstrap();
            bootstrap.group(ctx.channel().eventLoop()) // 注册线程池
                    .channel(NioSocketChannel.class) // 使用NioSocketChannel来作为连接用的channel类
                    .handler(new HttpProxyClientInitializer(ctx.channel()));
            BootstrapManage.bootstrapMap.put(ctx.channel().eventLoop(),bootstrap);
        }else{
            bootstrap = BootstrapManage.bootstrapMap.get(ctx.channel().eventLoop());
        }


        ChannelFuture cf = bootstrap.connect(targetIp, Integer.parseInt(targetPort));
        cf.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ch = cf.channel();
                } else {
                    ctx.channel().close();
                }
            }
        });
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            request.headers().set("Host", rewriteHost);
            log.info("读取到的消息:{}", msg);
            log.info("read 客户端channel{}", ctx.channel());
            log.info("queue的大小{}",queue.size());
            if (ch == null) {
                boolean offer = queue.offer(request);
                if (!offer) {
                    System.out.println("消息过多");
                    ctx.channel().writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.wrappedBuffer("消息堆积过多,服务端连接异常".getBytes())));
                    throw new Exception("消息堆积过多");
                }
                System.out.println("ch == null 情况");

            } else {
                log.info("read 服务端channel{}", ch);
                if (queue.peek() == null) {
                    System.out.println("peek null");
                    ch.writeAndFlush(request);
                } else {
                    System.out.println("peek not null");
                    while (queue.peek() != null) {
                        ch.writeAndFlush(queue.poll());
                    }
                    ch.writeAndFlush(request);
                }
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
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//        System.out.println(evt);
        if (evt instanceof IdleStateEvent) {
            // 入站的消息就是 IdleStateEvent 具体的事件
            IdleStateEvent event = (IdleStateEvent) evt;
            System.out.println(event.state());
            // 所以，这里我们需要判断事件类型
            switch (event.state()) {
                case READER_IDLE:
                    readIdleTimes++; // 读空闲的计数加 1
                    break;
                case WRITER_IDLE:
                    break; // 不处理
                case ALL_IDLE:
                    break; // 不处理
            }


            // 当读超时超过 3 次，我们就端口该客户端的连接
            // 注：读超时超过 3 次，代表起码有 4 次 10s 内客户端没有发送心跳包或普通数据包
            if (readIdleTimes > 3) {
                System.out.println("读超时消息");
//            ctx.channel().writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_TIMEOUT, Unpooled.wrappedBuffer("读超时3次 关闭连接！".getBytes())));
                ctx.channel().close(); // 手动断开连接
                ch.close();
                log.info("读超时，关闭两端长连接成功");
            }
        }else{
            System.out.println("userEventTriggered not IdleStateEvent");
            super.userEventTriggered(ctx, evt);
        }
    }


    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        end = System.currentTimeMillis();
        log.info("程序运行时间:{}",end-start);
        System.out.println("Channel-移除");
        super.channelUnregistered(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // 当触发handlerRemoved,ChannelGroup会自动移除客户端的Channel
        System.out.println("客户端断开, Channel对应的短ID：" + ctx.channel().id().asShortText());
    }


}