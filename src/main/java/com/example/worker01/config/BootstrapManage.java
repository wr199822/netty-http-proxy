package com.example.worker01.config;

import com.example.worker01.client.HttpProxyClientInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wangrui
 * @description
 * @date 2023年01月05日 10:18
 */
@Slf4j
@Component
public class BootstrapManage {

    public static final AttributeKey<String> SET_SERVER_CHANNEL = AttributeKey.newInstance("setServerChannel");

    public static Map<EventLoop, Bootstrap> bootstrapMap = new ConcurrentHashMap<>();

    public static Bootstrap getBootstrap(EventLoop eventLoop){
        //连接至目标服务器
        Bootstrap bootstrap ;
        if (bootstrapMap.get(eventLoop)==null){
            bootstrap = new Bootstrap();
            bootstrap.group(eventLoop) // 注册线程池
                    .channel(NioSocketChannel.class) // 使用NioSocketChannel来作为连接用的channel类
                    .handler(new HttpProxyClientInitializer())
                    .option(ChannelOption.TCP_NODELAY,true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);
            bootstrapMap.putIfAbsent(eventLoop, bootstrap);
        }else{
            bootstrap = bootstrapMap.get(eventLoop);
        }
        return bootstrap;
    }

    
}
