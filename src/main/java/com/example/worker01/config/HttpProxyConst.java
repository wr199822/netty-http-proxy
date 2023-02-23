package com.example.worker01.config;


import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.client.WebClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.lang.model.element.VariableElement;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author wangrui
 * @description
 * 1.volatile 不是很合适 因为对于全局变量的增加和减少 并不是一个原子操作 需要先读在写 这样volatile是不能保证线程安全的
 * 2.读写锁  可以但是不是很合适  因为读写锁适用于读多写少的情况 而当前的情景并不是
 * 3. synchronized 可以  也挺合适  如果tps不高的话  由于偏向锁和轻量级的存在 那么可能拿到锁的一直都是同一个线程就节省了锁释放的相关资源，
 * 而因为netty设计的原因 负责请求转发的几个线程就几个所以很大概率是 同一个线程持有锁 ，不过也因为netty设计的原因 如果tps高的话那么还是很容易膨胀为重量级的锁
 * 4.使用cas应该是比较合适的 因为它节省了阻塞线程而进行状态切换的时间 然后又因为这个netty中处理的线程是固定的 并不会照成长时间的自旋 如果tps太高的话 阻塞和等待应该也是在selector哪里等待了
 * 等走到这一步应该是业务层面 所以cas自旋消耗的cpu性能是可预见的，能同时竞争的也就两三个线程，并不会等待太久
 * @date 2023年01月15日 20:48
 */
@Component
public class HttpProxyConst {

//    public static CloseableHttpClient httpClient = HttpClients.createDefault();
   public static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(20, 30,
        10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10000),
        new ThreadPoolExecutor.AbortPolicy());
    public static int PendingRequestQueueGlobalMaxSize = 10000;

    private static Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(4));

    private static WebClient webClient = WebClient.create(vertx);

    private  static AtomicInteger PendingRequestQueueGlobalSize = new AtomicInteger(0);

    public  static void addPendingRequestQueueGlobalSize() {
        PendingRequestQueueGlobalSize.getAndIncrement();
    }

    public static void reducePendingRequestQueueGlobalSize() {
        reducePendingRequestQueueGlobalSize(1);
    }

    public static boolean checkPendingRequestQueueGlobalSize() {
        if (PendingRequestQueueGlobalSize.incrementAndGet()>PendingRequestQueueGlobalMaxSize){
            PendingRequestQueueGlobalSize.decrementAndGet();
            return true;
        }
        return false;
    }


    public static void reducePendingRequestQueueGlobalSize(int size) {
        if (PendingRequestQueueGlobalSize.get()<0){
            PendingRequestQueueGlobalSize.getAndSet(0);
        }
        PendingRequestQueueGlobalSize.getAndSet(PendingRequestQueueGlobalSize.get()-size);
    }

    public static WebClient getWebClient(){
        return webClient;
    }
}
