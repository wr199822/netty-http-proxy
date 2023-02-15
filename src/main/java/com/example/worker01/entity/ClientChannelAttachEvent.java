package com.example.worker01.entity;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;

import java.util.Queue;

/**
 * @author wangrui
 * @description
 * @date 2023年01月13日 19:33
 */
public class ClientChannelAttachEvent {

    private Channel channel;

    public ClientChannelAttachEvent(Channel channel) {
        this.channel = channel;
    }

    public ClientChannelAttachEvent() {
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }
}
