package com.example.worker01.config;

import io.netty.channel.Channel;

/**
 * @author wangrui
 * @description
 * @date 2023年01月13日 19:33
 */
public class HttpProxyEvent {

    private Channel channel;
    private String type;  //0是传递channel  1是传递状态

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
