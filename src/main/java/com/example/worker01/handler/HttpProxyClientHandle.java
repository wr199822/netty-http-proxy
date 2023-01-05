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
        clientChannel.writeAndFlush(msg);
    }


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // 入站的消息就是 IdleStateEvent 具体的事件
        IdleStateEvent event = (IdleStateEvent) evt;

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
        // 注：读超时超过 3 次，代表起码有 4 次 10S 内客户端没有发送心跳包或普通数据包
        if (readIdleTimes > 3) {
            ctx.channel().writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_TIMEOUT, Unpooled.wrappedBuffer("未接收到回复 关闭连接！".getBytes())));
            ctx.channel().close(); // 手动断开连接
            log.info("读超时，关闭服务端长连接成功");
        }


    }
}