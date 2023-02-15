package com.example.worker01.entity;

/**
 * @author wangrui
 * @description
 * @date 2023年02月15日 16:10
 */
public class ClientReplyStatusTransitionEvent {
    // 定义一个单例  用==来取代instanceOf提高性能
    private static ClientReplyStatusTransitionEvent instance = new ClientReplyStatusTransitionEvent();

    private ClientReplyStatusTransitionEvent() {
    }

    public static ClientReplyStatusTransitionEvent getInstance() {
        return instance;
    }
}
