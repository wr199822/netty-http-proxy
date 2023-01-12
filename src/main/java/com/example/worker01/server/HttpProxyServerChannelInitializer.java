package com.example.worker01.server;

import com.example.worker01.handler.HttpProxyServerHandle;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;



@Component
public class HttpProxyServerChannelInitializer extends ChannelInitializer<SocketChannel> implements ChannelHandler {


    @Value("${netty.target-ip}")
    private String targetIp;

    @Value("${netty.target-port}")
    private String targetPort;

    @Value("${netty.rewrite-host}")
    private String rewriteHost;



    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        ch.pipeline().addLast("httpCodec",new HttpServerCodec());
        ch.pipeline().addLast("httpObject",new HttpObjectAggregator(65536));
//        ch.pipeline().addLast(new IdleStateHandler(10,0,0));
        //2.自定义处理Http的业务Handler
        pipeline.addLast("httpProxyServerHandle",new HttpProxyServerHandle(targetIp,targetPort,rewriteHost));
    }
}

