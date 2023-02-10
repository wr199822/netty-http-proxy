package com.example.worker01.config;


import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author wangrui
 * @description
 * @date 2023年01月15日 20:48
 */
public class HttpProxyConst {
    /**
     * 1.volatile 不是很合适 因为对于全局变量的增加和减少 并不是一个原子操作 需要先读在写 这样volatile是不能保证线程安全的
     *
     * 2.读写锁  可以但是不是很合适  因为读写锁适用于读多写少的情况 而当前的情景并不是
     *
     * 3. synchronized 可以  也挺合适  如果tps不高的话  由于偏向锁和轻量级的存在 那么可能拿到锁的一直都是同一个线程就节省了锁释放的相关资源，
     * 而因为netty设计的原因 负责请求转发的几个线程就几个所以很大概率是 同一个线程持有锁 ，不过也因为netty设计的原因 如果tps高的话那么还是很容易膨胀为重量级的锁
     *
     * 4.使用cas应该是比较合适的 因为它节省了阻塞线程而进行状态切换的时间 然后又因为这个netty中处理的线程是固定的 并不会照成长时间的自旋 如果tps太高的话 阻塞和等待应该也是在selector哪里等待了
     * 等走到这一步应该是业务层面 所以cas自旋消耗的cpu性能是可预见的
     *
     * */
    private  static AtomicInteger PendingRequestQueueGlobalSize = new AtomicInteger(0);

    private static AtomicInteger checkLockStatus = new AtomicInteger(0);

    public static int  PendingRequestQueueGlobalMaxSize = 10000;

    public  static int addPendingRequestQueueGlobalSize() {
        return PendingRequestQueueGlobalSize.getAndIncrement();
    }

    public static int reducePendingRequestQueueGlobalSize() {
        if (PendingRequestQueueGlobalSize.get()<0){
            return 0;
        }
        return PendingRequestQueueGlobalSize.getAndDecrement();
    }

    public static boolean checkPendingRequestQueueGlobalSize() {
        for (;;){
            int i = checkLockStatus.get();
            int update = ++i;
            if (i>10000){
                update = 0;
            }
            boolean b = checkLockStatus.compareAndSet(i, update);
            if (b){
                return PendingRequestQueueGlobalSize.get()>=PendingRequestQueueGlobalMaxSize;
            }
        }
    }

    public static int getPendingRequestQueueGlobalSize() {
        return PendingRequestQueueGlobalSize.get();
    }

}
