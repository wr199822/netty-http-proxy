package com.example.worker01.config;

/**
 * @author wangrui
 * @description
 * @date 2023年01月15日 20:48
 */
public class HttpProxyConst {

    private static int  PendingRequestQueueGlobalSize = 0;

    public static int  PendingRequestQueueGlobalMaxSize = 10000;

    public static int addPendingRequestQueueGlobalSize() {
        return PendingRequestQueueGlobalSize++;
    }

    public static int reducePendingRequestQueueGlobalSize() {
        if (PendingRequestQueueGlobalSize<0){
            return 0;
        }
        return PendingRequestQueueGlobalSize--;
    }

    public static boolean checkPendingRequestQueueGlobalSize() {
        return PendingRequestQueueGlobalSize<=PendingRequestQueueGlobalMaxSize;
    }

    public static int getPendingRequestQueueGlobalSize() {
        return PendingRequestQueueGlobalSize;
    }

}
