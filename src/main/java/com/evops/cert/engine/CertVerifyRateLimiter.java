package com.evops.cert.engine;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验真窗口小流量限流器：单实例内存固定窗口（每分钟计数）。
 * 当前为 H2 单文件库单实例部署，内存计数足够；若日后多实例，改为 Redis/数据库共享计数。
 */
@Component
public class CertVerifyRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final AtomicInteger count = new AtomicInteger(0);
    private volatile long windowStart = 0L;

    /**
     * 尝试在当前分钟窗口占用一次请求。
     *
     * @param limitPerMinute 每分钟上限，&lt;=0 表示不限
     * @return true 放行；false 表示已超小流量上限
     */
    public synchronized boolean tryAcquire(int limitPerMinute) {
        if (limitPerMinute <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - windowStart >= WINDOW_MILLIS) {
            windowStart = now;
            count.set(0);
        }
        return count.incrementAndGet() <= limitPerMinute;
    }

    /** 清空计数窗口（调整限流阈值或测试时用）。 */
    public synchronized void reset() {
        windowStart = 0L;
        count.set(0);
    }
}
