package com.example.worker01.server;

import com.example.worker01.handler.HttpProxyServerHandle;
import com.example.worker01.handler.HttpProxyServerHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

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
        // server端发送的是httpResponse，所以要使用HttpResponseEncoder进行编码
        ch.pipeline().addLast(
                new HttpResponseEncoder());
        // server端接收到的是httpRequest，所以要使用HttpRequestDecoder进行解码
        ch.pipeline().addLast(
                new HttpRequestDecoder());
        //2.自定义处理Http的业务Handler
        pipeline.addLast(httpProxyServerHandle);
    }
}

