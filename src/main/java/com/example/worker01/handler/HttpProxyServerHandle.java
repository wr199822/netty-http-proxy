package com.example.worker01.handler;

import com.example.worker01.config.HttpProxyConst;
import com.example.worker01.entity.ClientChannelAttachEvent;
import com.example.worker01.entity.TargetChannelDisconnectEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.Queue;

@Slf4j
public class HttpProxyServerHandle extends ChannelInboundHandlerAdapter {

    private Channel clientChannel;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        log.info("接收服务端response{}",msg);
        ChannelFuture channelFuture = clientChannel.writeAndFlush(msg);
        channelFuture.addListener((ChannelFutureListener) future -> {
            Throwable cause = future.cause();
            if (cause!=null){
                future.cause().printStackTrace();
                ((FullHttpResponse)msg).release();
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //服务端连接关闭了 客户端不动  但是下次客户端消息来了 需要重新连接服务端
        //  客户端关闭了  服务端也要关闭 并且释放相关资源
        TargetChannelDisconnectEvent targetChannelDisconnectEvent = new TargetChannelDisconnectEvent();
        clientChannel.pipeline().fireUserEventTriggered(targetChannelDisconnectEvent);
        //如果客户端关闭 是先关闭的客户端连接 在关闭的服务端连接 如果这个时间点服务端来消息了 就会照成内存泄漏
        //这部分逻辑迁移到了 写失败
//        fullHttpResponse.content().release();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        log.info("read 服务端channel{}", ctx.channel());
        if (evt instanceof ClientChannelAttachEvent){
            ClientChannelAttachEvent clientChannelAttachEvent = (ClientChannelAttachEvent) evt;
            this.clientChannel = clientChannelAttachEvent.getChannel();
            Queue<FullHttpRequest> pendingRequestQueue = clientChannelAttachEvent.getPendingRequestQueue();
            // 这里保证了 queue 并不会堆积
            while (pendingRequestQueue.peek() != null) {
                FullHttpRequest fullHttpRequest = pendingRequestQueue.poll();
                HttpProxyConst.reducePendingRequestQueueGlobalSize();
                ctx.writeAndFlush(fullHttpRequest).addListener((ChannelFutureListener) future -> {
                    Throwable cause = future.cause();
                    if (cause!=null){
                        future.cause().printStackTrace();
                        fullHttpRequest.content().release();
                    }
                });
            }
        }
    }
}