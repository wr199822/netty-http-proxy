package com.example.worker01.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Slf4j
@Component
public class HttpProxyServer {

    @Value("${netty.port}")
    private int port;

    private NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);

    private NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);


    @Autowired
    private HttpProxyServerChannelInitializer httpProxyServerChannelInitializer;


    /**
     * 启动Server
     *
     * @throws InterruptedException
     */
    @PostConstruct
    public void start() throws InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(httpProxyServerChannelInitializer)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                .option(ChannelOption.SO_BACKLOG, 1024) //服务端可连接队列数,对应TCP/IP协议listen函数中backlog参数
                .childOption(ChannelOption.TCP_NODELAY, true)//立即写出
                .childOption(ChannelOption.SO_KEEPALIVE, true);//长连接

        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.SIMPLE);//内存泄漏检测 开发推荐PARANOID 线上SIMPLE
        ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
        if (channelFuture.isSuccess()) {
            log.info("TCP服务启动完毕,port={}", this.port);
        }
    }

    /**
     * 销毁资源
     */
    @PreDestroy
    public void destroy() {
        bossGroup.shutdownGracefully().syncUninterruptibly();
        workerGroup.shutdownGracefully().syncUninterruptibly();
        log.info("关闭成功");
    }


}
