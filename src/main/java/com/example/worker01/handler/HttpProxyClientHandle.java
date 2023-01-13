package com.example.worker01.handler;

import com.example.worker01.config.BootstrapManage;
import com.example.worker01.config.HttpProxyEvent;
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
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //服务端连接关闭了 客户端不动  但是下次客户端消息来了 需要重新连接服务端
        // 需要 客户端关闭了  服务端也要关闭 并且释放相关资源
        HttpProxyEvent httpProxyEvent = new HttpProxyEvent();
        httpProxyEvent.setType("1");
        clientChannel.pipeline().fireUserEventTriggered(httpProxyEvent);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        log.info("read 服务端channel{}", ctx.channel());
        if (evt instanceof HttpProxyEvent && ((HttpProxyEvent) evt).getType().equals("0")){
            this.clientChannel = ((HttpProxyEvent) evt).getChannel();
        }
    }
}