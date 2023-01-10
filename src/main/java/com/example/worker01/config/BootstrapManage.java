package com.example.worker01.config;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoop;
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

    public static Bootstrap put(EventLoop eventLoop,Bootstrap bootstrap){
        Bootstrap put = bootstrapMap.putIfAbsent(eventLoop, bootstrap);
        return put;
    }

    public static Bootstrap get(EventLoop eventLoop){
        Bootstrap get = bootstrapMap.get(eventLoop);
        return get;
    }
    
}
