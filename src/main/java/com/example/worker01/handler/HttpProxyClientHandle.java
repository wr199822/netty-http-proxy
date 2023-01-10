package com.example.worker01.handler;

import com.example.worker01.config.BootstrapManage;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpProxyClientHandle extends ChannelInboundHandlerAdapter {



    private Channel clientChannel;


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        clientChannel.writeAndFlush(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof Channel && ((Channel) evt).attr(BootstrapManage.SET_SERVER_CHANNEL).get().equals("1")){
            this.clientChannel = (Channel) evt;
        }else{
            super.userEventTriggered(ctx, evt);
        }
    }
}