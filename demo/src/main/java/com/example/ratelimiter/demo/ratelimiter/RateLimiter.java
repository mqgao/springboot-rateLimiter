package com.example.ratelimiter.demo.ratelimiter;/*
 * Created by gao.mq on 2018-06-26
 * desc：RateLimiter实现类
 *   在分布式环境中，获取时间需要使用redis的时间
 *  lua脚本 String script = "local a=redis.call('TIME');return (a[1]*1000000+a[2])/1000";
 */

import com.google.common.base.Preconditions;
import com.google.common.math.LongMath;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.lang.Math.min;

@Slf4j
@Data
public class RateLimiter {
    private String key;  //Redis key
    private Long permitsPerSecond; //每秒放入的令牌数
    private int maxBurstSeconds; //最大存储maxBurstSeconds秒生成的令牌
    private SyncLock syncLock;  // 分布式锁
    RedisTemplate<String, RedisPermits> redisTemplate;


    public RateLimiter(String key, Long permitsPerSecond, int maxBurstSeconds, SyncLock syncLock, RedisTemplate<String, RedisPermits> redisTemplate) {
        this.key = key;
        this.permitsPerSecond = permitsPerSecond;
        this.maxBurstSeconds = maxBurstSeconds;
        this.syncLock = syncLock;
        this.redisTemplate=redisTemplate;
    }

    /**
     * 生成并存储默认令牌桶
     */
    private RedisPermits putDefaultPermits() {
        RedisPermits permits = new RedisPermits(permitsPerSecond, maxBurstSeconds, System.currentTimeMillis());
        redisTemplate.opsForValue().set(key,permits,permits.expires(),TimeUnit.SECONDS);
        return permits;
    }

    /**
     * 获取/更新令牌桶
     */
    RedisPermits permit;

    private RedisPermits getPermits() {
        if (StringUtils.isEmpty(redisTemplate.opsForValue().get(key))) {
            return putDefaultPermits();
        } else {
            return redisTemplate.opsForValue().get(key);
        }
    }

    private void setPermits(RedisPermits permits) {
        redisTemplate.opsForValue().set(key,permits,permits.expires(),TimeUnit.SECONDS);
    }

    /**
     * Acquires the given number of tokens from this {@code RateLimiter}, blocking until the request
     * can be granted. Tells the amount of time slept, if any.
     *
     * @param tokens the number of tokens to acquire
     * @return time spent sleeping to enforce rate, in milliseconds; 0 if not rate-limited
     * @throws IllegalArgumentException if the requested number of token is negative or zero
     */
    public Long acquire(Long tokens) throws IllegalArgumentException, InterruptedException {
        Long milliToWait = reserve(tokens);
        log.info("acquire for {}ms {}", milliToWait, Thread.currentThread().getName());
        Thread.sleep(milliToWait);
        return milliToWait;
    }

    /**
     * Acquires a single token from this {@code RateLimiter}, blocking until the request can be
     * granted. Tells the amount of time slept, if any.
     * <p>
     * <p>This method is equivalent to {@code acquire(1)}.
     *
     * @return time spent sleeping to enforce rate, in milliseconds; 0 if not rate-limited
     */
    public void acquire() throws InterruptedException, IllegalArgumentException {
        acquire(1L);
    }

    /**
     * Acquires the given number of tokens from this {@code RateLimiter} if it can be obtained
     * without exceeding the specified {@code timeout}, or returns {@code false} immediately (without
     * waiting) if the tokens would not have been granted before the timeout expired.
     *
     * @param tokens  the number of tokens to acquire
     * @param timeout the maximum time to wait for the tokens. Negative values are treated as zero.
     * @param unit    the time unit of the timeout argument
     * @return {@code true} if the tokens were acquired, {@code false} otherwise
     * @throws IllegalArgumentException if the requested number of token is negative or zero
     */
    public boolean tryAcquire(Long tokens, Long timeout, TimeUnit unit) throws IllegalArgumentException, InterruptedException {
        long timeoutMicros = max(unit.toMillis(timeout), 0);
        checkTokens(tokens);
        Long milliToWait;
        try {
            syncLock.lock();
            if (!canAcquire(tokens, timeoutMicros)) {
                return false;
            } else {
                milliToWait = reserveAndGetWaitLength(tokens);
            }
        } finally {
            syncLock.unLock();
        }
        Thread.sleep(milliToWait);
        return true;
    }

    /**
     * Acquires a token from this {@code RateLimiter} if it can be obtained without exceeding the
     * specified {@code timeout}, or returns {@code false} immediately (without waiting) if the token
     * would not have been granted before the timeout expired.
     * <p>
     * <p>This method is equivalent to {@code tryAcquire(1, timeout, unit)}.
     *
     * @param timeout the maximum time to wait for the token. Negative values are treated as zero.
     * @param unit    the time unit of the timeout argument
     * @return {@code true} if the token was acquired, {@code false} otherwise
     * @throws IllegalArgumentException if the requested number of token is negative or zero
     */
    public boolean tryAcquire(Long timeout, TimeUnit unit) throws IllegalArgumentException, InterruptedException {
        return tryAcquire(1L, timeout, unit);
    }

    /**
     * 获取redis服务器时间
     */
    private Long now() {
        return System.currentTimeMillis();
    }

    /**
     * Reserves the given number of tokens from this {@code RateLimiter} for future use, returning
     * the number of milliseconds until the reservation can be consumed.
     *
     * @param tokens the number of tokens to acquire
     * @return time in milliseconds to wait until the resource can be acquired, never negative
     * @throws IllegalArgumentException if the requested number of tokens is negative or zero
     */
    private Long reserve(Long tokens) throws IllegalArgumentException, InterruptedException {
        checkTokens(tokens);
        try {
            syncLock.lock();
            return reserveAndGetWaitLength(tokens);
        } finally {
            syncLock.unLock();
        }
    }

    private void checkTokens(Long tokens) throws IllegalArgumentException {
        Preconditions.checkArgument(tokens > 0, "Requested tokens $tokens must be positive");
    }

    private boolean canAcquire(Long tokens, Long timeoutMillis) {
        return queryEarliestAvailable(tokens) - timeoutMillis <= 0;
    }

    /**
     * Returns the earliest milliseconds to wait that tokens are available (with one caveat).
     *
     * @param tokens the number of tokens to acquire
     * @return the milliseconds to wait that tokens are available, or, if tokens are available immediately, zero or positive
     */
    private Long queryEarliestAvailable(Long tokens) {
        long n = now();
        RedisPermits permit = getPermits();
        permit.reSync(n);
        long storedPermitsToSpend = min(tokens, permit.storedPermits); // 可以消耗的令牌数
        long freshPermits = tokens - storedPermitsToSpend; // 需要等待的令牌数
        long waitMillis = freshPermits * permit.intervalMillis; // 需要等待的时间
        return LongMath.saturatedAdd(permit.nextFreeTicketMillis - n, waitMillis);
    }

    /**
     * Reserves next ticket and returns the wait time that the caller must wait for.
     *
     * @param tokens the number of tokens to acquire
     * @return the required wait time, never negative
     */
    private Long reserveAndGetWaitLength(Long tokens) {
        long n = now();
        RedisPermits permit = getPermits();
        permit.reSync(n);
        long storedPermitsToSpend = min(tokens, permit.storedPermits); // 可以消耗的令牌数
        long freshPermits = tokens - storedPermitsToSpend;// 需要等待的令牌数
        long waitMillis = freshPermits * permit.intervalMillis; // 需要等待的时间
        permit.nextFreeTicketMillis = LongMath.saturatedAdd(permit.nextFreeTicketMillis, waitMillis);
        permit.storedPermits -= storedPermitsToSpend;
        setPermits(permit);
        return permit.nextFreeTicketMillis - n;
    }


}
