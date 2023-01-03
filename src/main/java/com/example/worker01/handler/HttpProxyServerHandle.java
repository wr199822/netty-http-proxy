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
        if (cf == null) {
            //连接至目标服务器
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(new NioEventLoopGroup(4)) // 注册线程池
                    .channel(NioSocketChannel.class) // 使用NioSocketChannel来作为连接用的channel类
                    .handler(new HttpProxyClientInitializer(ctx.channel()));

            cf = bootstrap.connect(targetIp, Integer.parseInt(targetPort));
            cf.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        //修改msg中的Host
                        FullHttpRequest request = (FullHttpRequest) msg;
                        request.headers().set("Host", rewriteHost);
                        future.channel().writeAndFlush(request);
                    } else {
                        ctx.channel().close();
                    }
                }
            });
        }else{
            cf.channel().writeAndFlush(msg);
        }
    }


}