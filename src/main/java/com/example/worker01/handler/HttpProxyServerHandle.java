package com.example.worker01.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@ChannelHandler.Sharable
public class HttpProxyServerHandle extends ChannelInboundHandlerAdapter {


    @Value("${netty.target_ip}")
    private String target_ip;

    @Value("${netty.target_port}")
    private String target_port;

    @Value("${netty.rewrite_host}")
    private String rewrite_host;

    private ChannelFuture cf;

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            //连接至目标服务器
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(ctx.channel().eventLoop()) // 注册线程池
                    .channel(ctx.channel().getClass()) // 使用NioSocketChannel来作为连接用的channel类
                    .handler(new HttpProxyInitializer(ctx.channel()));

            ChannelFuture cf = bootstrap.connect(target_ip, Integer.parseInt(target_port));
            cf.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        //修改msg中的Host
                        FullHttpRequest request = (FullHttpRequest)msg;
                        request.headers().set("Host",rewrite_host);
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
                cf = bootstrap.connect(target_ip, Integer.parseInt(target_port));
                cf.addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
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