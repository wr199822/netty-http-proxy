package com.example.worker01.entity;

import io.netty.channel.Channel;

/**
 * @author wangrui
 * @description
 * @date 2023年01月13日 19:33
 */
public class TargetChannelDisconnectEvent {

    // 定义一个单例  用==来取代instanceOf提高性能
    private static class TargetChannelDisconnectEventHolder {
        private static TargetChannelDisconnectEvent instance = new TargetChannelDisconnectEvent();
    }

    private TargetChannelDisconnectEvent() {
    }

    public static TargetChannelDisconnectEvent getInstance() {
        return TargetChannelDisconnectEventHolder.instance;
    }
}
