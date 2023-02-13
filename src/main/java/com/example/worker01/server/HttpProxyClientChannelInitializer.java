package com.example.worker01.server;

import com.example.worker01.handler.HttpProxyClientHandle;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;



@Component
public class HttpProxyClientChannelInitializer extends ChannelInitializer<SocketChannel> implements ChannelHandler {


    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        ch.pipeline().addLast("httpCodec",new HttpServerCodec());
        ch.pipeline().addLast("httpObject",new HttpObjectAggregator(65536));
//        ch.pipeline().addLast(new IdleStateHandler(10,0,0));
        //2.自定义处理Http的业务Handler
        pipeline.addLast("httpProxyServerHandle",new HttpProxyClientHandle());
    }
}

