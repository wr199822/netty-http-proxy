package com.example.worker01.config;


import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author wangrui
 * @description
 * @date 2023年01月15日 20:48
 */
public class HttpProxyConst {
    /**
     * 1.volatile可以但是不合适 因为这个场景是写多然后读少 而volatile每次写都需要把缓存行里面的数据刷新到内存 然后其他处理器就需要把自己的缓存行置为无效
     *    这样就不能充分发挥处理器缓冲行的优势 相反还有一定的性能损失
     *
     * 2.
     * */
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
