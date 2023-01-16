package com.example.worker01.config;


import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author wangrui
 * @description
 * @date 2023年01月15日 20:48
 */
public class HttpProxyConst {

    private static AtomicInteger PendingRequestQueueGlobalSize = new AtomicInteger(0);

    public static int  PendingRequestQueueGlobalMaxSize = 10000;

    public static int addPendingRequestQueueGlobalSize() {
        return PendingRequestQueueGlobalSize.getAndIncrement();
    }

    public static int reducePendingRequestQueueGlobalSize() {
        if (PendingRequestQueueGlobalSize.get()<0){
            return 0;
        }
        return PendingRequestQueueGlobalSize.getAndDecrement();
    }

    public static boolean checkPendingRequestQueueGlobalSize() {
        return PendingRequestQueueGlobalSize.get()<=PendingRequestQueueGlobalMaxSize;
    }

    public static int getPendingRequestQueueGlobalSize() {
        return PendingRequestQueueGlobalSize.get();
    }

}
