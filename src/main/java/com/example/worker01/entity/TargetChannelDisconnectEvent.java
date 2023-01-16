package com.example.worker01.entity;

import io.netty.channel.Channel;

/**
 * @author wangrui
 * @description
 * @date 2023年01月13日 19:33
 */
public class TargetChannelDisconnectEvent {

    private String type;  //  1是传递状态

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
