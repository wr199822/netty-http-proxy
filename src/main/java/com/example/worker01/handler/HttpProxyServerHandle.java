package com.example.worker01.handler;

import com.example.worker01.client.HttpProxyClientInitializer;
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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TransferQueue;


@Slf4j
@Component
public class HttpProxyServerHandle extends ChannelInboundHandlerAdapter {


    private String targetIp;

    private String targetPort;

    private String rewriteHost;

    private int readIdleTimes;

    //先放这里 如果以后其他地方需要用到在抽取出来
    private Map<String, Bootstrap> bootstrapMap = new ConcurrentHashMap<>();

    private Channel ch;

    private ArrayBlockingQueue<FullHttpRequest> queue = new ArrayBlockingQueue<FullHttpRequest>(1024);

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
        //连接至目标服务器
        Bootstrap bootstrap ;
        if (bootstrapMap.get(ip)==null){
            bootstrap = new Bootstrap();
            bootstrap.group(ctx.channel().eventLoop()) // 注册线程池
                    .channel(NioSocketChannel.class) // 使用NioSocketChannel来作为连接用的channel类
                    .handler(new HttpProxyClientInitializer(ctx.channel()))
                    .option(ChannelOption.TCP_NODELAY, true)//立即写出
                    .option(ChannelOption.SO_KEEPALIVE, true);//长连接
            bootstrapMap.put(ip,bootstrap);
        }else{
            bootstrap = bootstrapMap.get(ip);
        }


        ChannelFuture cf = bootstrap.connect(targetIp, Integer.parseInt(targetPort));
        cf.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ch = cf.channel();
                } else {
                    ctx.channel().close();
                }
            }
        });
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        FullHttpRequest request = (FullHttpRequest) msg;
        request.headers().set("Host", rewriteHost);
        if (ch == null) {
            //ch==null 可能是Target 连接正在建也可能是连接不成功 我们先把消息存起来
            //如果消息堆积过多应该怎么办呢？
            boolean offer = queue.offer(request);
            if (!offer){
                ctx.channel().writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.wrappedBuffer("消息堆积过多,服务端连接异常".getBytes())));
                throw new Exception("消息堆积过多");
            }

        }else{
            if (queue.peek()==null){
                ch.writeAndFlush(request);
            }else{
                while (queue.peek() != null) {
                    ch.writeAndFlush(queue.poll());
                }
            }
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
            ctx.channel().writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_TIMEOUT, Unpooled.wrappedBuffer("读超时3次 关闭连接！".getBytes())));
            ctx.channel().close(); // 手动断开连接
            log.info("读超时，关闭客户端{}长连接成功",ctx.channel().remoteAddress());
        }
    }






}