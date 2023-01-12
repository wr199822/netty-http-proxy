package com.example.worker01.handler;

import com.example.worker01.config.BootstrapManage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpProxyClientHandle extends ChannelInboundHandlerAdapter {

    private Channel clientChannel;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        log.info("接收服务端response{}",msg);
        clientChannel.writeAndFlush(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        log.info("read 服务端channel{}", ctx.channel());
        if (evt instanceof Channel && ((Channel) evt).attr(BootstrapManage.SET_SERVER_CHANNEL).get().equals("1")){
            this.clientChannel = (Channel) evt;
        }else{
            super.userEventTriggered(ctx, evt);
        }
    }
}