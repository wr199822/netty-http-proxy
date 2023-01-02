package com.example.worker01.server;

import com.example.worker01.handler.HttpProxyServerHandle;
import com.example.worker01.handler.HttpProxyServerHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import io.netty.handler.codec.http.HttpServerCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class JT808ChannelInitializer extends ChannelInitializer<SocketChannel> implements ChannelHandler {



    @Autowired
    private HttpProxyServerHandler httpProxyServerHandler;

    @Autowired
    private HttpProxyServerHandle httpProxyServerHandle;


    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        ch.pipeline().addLast("httpCodec",new HttpServerCodec());
        ch.pipeline().addLast("httpObject",new HttpObjectAggregator(65536));
        //2.自定义处理Http的业务Handler
        pipeline.addLast(httpProxyServerHandle);
    }
}

