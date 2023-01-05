package com.example.worker01.config;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
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

    private Map<String, Bootstrap> channelMap = new ConcurrentHashMap<>();
    
    //判断bootstrap是否存在
    
}
