package com.example.worker01.handler;

import com.example.worker01.entity.ClientChannelAttachEvent;
import com.example.worker01.entity.ClientReplyStatusTransitionEvent;
import com.example.worker01.entity.TargetChannelDisconnectEvent;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class HttpProxyServerHandle extends ChannelInboundHandlerAdapter {

    private Channel clientChannel;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)  {
        //只要write不管成功还是失败 不用管内存空间是否释放成功
        clientChannel.writeAndFlush(msg);
        clientChannel.pipeline().fireUserEventTriggered(ClientReplyStatusTransitionEvent.getInstance());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx){
        //服务端连接关闭了 客户端不动  但是下次客户端消息来了 需要重新连接服务端
        //  客户端关闭了  服务端也要关闭 并且释放相关资源
        clientChannel.pipeline().fireUserEventTriggered(TargetChannelDisconnectEvent.getInstance());
        //如果客户端关闭 是先关闭的客户端连接 在关闭的服务端连接 如果这个时间点服务端来消息了 就会照成内存泄漏
        // fullHttpResponse.content().release();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)  {
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        log.info("read 服务端channel{}", ctx.channel());
        if (evt instanceof ClientChannelAttachEvent){
            this.clientChannel = ((ClientChannelAttachEvent) evt).getChannel();
        }
    }
}