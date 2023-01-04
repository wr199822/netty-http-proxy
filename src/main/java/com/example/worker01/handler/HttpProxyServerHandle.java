package com.example.worker01.handler;

import com.example.worker01.client.HttpProxyClientInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;


@Slf4j
@Component
public class HttpProxyServerHandle extends ChannelInboundHandlerAdapter {


    @Value("${netty.target-ip}")
    private String targetIp;

    @Value("${netty.target-port}")
    private String targetPort;

    @Value("${netty.rewrite-host}")
    private String rewriteHost;

    private Channel ch;

    private ArrayBlockingQueue<FullHttpRequest> queue = new ArrayBlockingQueue<FullHttpRequest>(1024);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //连接至目标服务器
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop()) // 注册线程池
                .channel(NioSocketChannel.class) // 使用NioSocketChannel来作为连接用的channel类
                .handler(new HttpProxyClientInitializer(ctx.channel()));

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
        FullHttpRequest request = (FullHttpRequest) msg;
        request.headers().set("Host", rewriteHost);
        if (ch == null) {
            //ch==null 可能是Target 连接正在建也可能是连接不成功 我们先把消息存起来
            //如果消息堆积过多应该怎么办呢？
            boolean offer = queue.offer(request);
            if (!offer){
                new Exception("消息堆积过多");
                ctx.channel().close();
            }

        }else{
            if (queue.peek()==null){
                ch.writeAndFlush(request);
            }else{
                while (queue.peek() != null) {
                    ch.writeAndFlush(queue.poll());
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






}