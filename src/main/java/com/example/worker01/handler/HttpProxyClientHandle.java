package com.example.worker01.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpProxyClientHandle extends ChannelInboundHandlerAdapter {

    private int readIdleTimes;

    private Channel clientChannel;

    public HttpProxyClientHandle(Channel clientChannel) {
        this.clientChannel = clientChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //这是写response消息的  用serverhandle的channel 给客户端发送response请求
        System.out.println("服务端消息"+(FullHttpResponse)msg);
        clientChannel.writeAndFlush(msg);
    }


}