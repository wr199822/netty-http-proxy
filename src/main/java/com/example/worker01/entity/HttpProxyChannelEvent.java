package com.example.worker01.entity;

import io.netty.channel.Channel;

/**
 * @author wangrui
 * @description
 * @date 2023年01月13日 19:33
 */
public class HttpProxyChannelEvent {

    private Channel channel;

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }
}
