package com.example.worker01.handler;

import com.example.worker01.client.HttpProxyClientInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@ChannelHandler.Sharable
public class HttpProxyServerHandle extends ChannelInboundHandlerAdapter {


    @Value("${netty.target-ip}")
    private String targetIp;

    @Value("${netty.target-port}")
    private String targetPort;

    @Value("${netty.rewrite-host}")
    private String rewriteHost;

    private ChannelFuture cf;

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            //连接至目标服务器
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(ctx.channel().eventLoop()) // 注册线程池
                    .channel(ctx.channel().getClass()) // 使用NioSocketChannel来作为连接用的channel类
                    .handler(new HttpProxyClientInitializer(ctx.channel()));

            ChannelFuture cf = bootstrap.connect(targetIp, Integer.parseInt(targetPort));
            cf.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        //修改msg中的Host
                        FullHttpRequest request = (FullHttpRequest)msg;
                        request.headers().set("Host",rewriteHost);
                        future.channel().writeAndFlush(request);
                    } else {
                        ctx.channel().close();
                    }
                }
            });
        } else {
            //cf 为空 说明是第一次连接
            if (cf == null) {
                //连接至目标服务器
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(ctx.channel().eventLoop()) // 复用客户端连接线程池
                        .channel(ctx.channel().getClass()) // 使用NioSocketChannel来作为连接用的channel类
                        .handler(new ChannelInitializer() {

                            @Override
                            protected void initChannel(Channel ch) throws Exception {
                                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx0, Object msg) throws Exception {
                                        ctx.channel().writeAndFlush(msg);
                                    }
                                });
                            }
                        });
                cf = bootstrap.connect(targetIp, Integer.parseInt(targetPort));
                cf.addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            //response 直接返回就好
                            //拿服务器的channel
                            future.channel().writeAndFlush(msg);
                        } else {
                            ctx.channel().close();
                        }
                    }
                });
            } else {
                //说明不是第一次连接 直接把response返回就行
                cf.channel().writeAndFlush(msg);
            }
        }
    }


}