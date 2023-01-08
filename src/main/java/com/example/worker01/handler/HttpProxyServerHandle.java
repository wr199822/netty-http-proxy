package com.example.worker01.handler;

import com.example.worker01.client.HttpProxyClientInitializer;
import com.example.worker01.config.BootstrapManage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TransferQueue;


@Slf4j
public class HttpProxyServerHandle extends ChannelInboundHandlerAdapter {


    private String targetIp;

    private String targetPort;

    private String rewriteHost;

    private int readIdleTimes;

    private ChannelFuture cf;
    //连接至目标服务器
    Bootstrap bootstrap ;




    public HttpProxyServerHandle(String targetIp, String targetPort, String rewriteHost) {
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.rewriteHost = rewriteHost;
    }

    public HttpProxyServerHandle() {
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress insocket = (InetSocketAddress)ctx.channel().remoteAddress();
        String ip = String.valueOf(insocket.getAddress());

        if (BootstrapManage.bootstrapMap.get(ctx.channel().eventLoop())==null){
            bootstrap = new Bootstrap();
            bootstrap.group(ctx.channel().eventLoop()) // 注册线程池
                    .channel(NioSocketChannel.class) // 使用NioSocketChannel来作为连接用的channel类
                    .handler(new HttpProxyClientInitializer(ctx.channel()));
            BootstrapManage.bootstrapMap.put(ctx.channel().eventLoop(),bootstrap);
        }else{
            bootstrap = BootstrapManage.bootstrapMap.get(ctx.channel().eventLoop());
        }
         cf = bootstrap.connect(targetIp, Integer.parseInt(targetPort));
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        FullHttpRequest request = (FullHttpRequest) msg;
        request.headers().set("Host", rewriteHost);
        //2. 这里的执行流程是
        if (cf == null) {
            cf.addListener(new ChannelFutureListener() {
                //1. 在连接成功之后会在加入到reactor 监听的异步任务中
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        //这一步其实就相当于把msg保存起来了
                        //修改msg中的Host
                        cf.channel().writeAndFlush(request);
                    } else {
                        ctx.channel().close();
                    }
                }
            });
        }else{
            cf.channel().writeAndFlush(request);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,
                                Throwable cause) {
        cause.printStackTrace();
        ctx.close();
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
        // 注：读超时超过 3 次，代表起码有 4 次 10s 内客户端没有发送心跳包或普通数据包
        if (readIdleTimes > 3) {
            System.out.println("读超时消息");
            ctx.channel().writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_TIMEOUT, Unpooled.wrappedBuffer("读超时3次 关闭连接！".getBytes())));
            ctx.channel().close(); // 手动断开连接
            cf.channel().close();
            log.info("读超时，关闭两端长连接成功");
        }
    }






}