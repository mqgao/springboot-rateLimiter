package com.example.ratelimiter.demo.ratelimiter;/*
 * Created by gao.mq on 2018-06-26
 * desc：SyncLock同步锁工厂类
 */

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SyncLock {
    public String key;
    public StringRedisTemplate stringRedisTemplate;
    public Long expire;
    public Long safetyTime;

    private Long waitMillisPer = 10L;

   public SyncLock(String key, StringRedisTemplate stringRedisTemplate, Long expire, Long safetyTime){
       this.key=key;
       this.stringRedisTemplate=stringRedisTemplate;
       this.expire=expire;
       this.safetyTime=safetyTime;
   }

    private String getValue() {
        return Thread.currentThread().getId()+"-"+Thread.currentThread().getName();
    }

    /**
     * 尝试获取锁（立即返回）
     *
     * @return 是否获取成功
     * @see [lock]
     * @see [unLock]
     */
    boolean tryLock() {
        boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, getValue());
        if (locked) {
            stringRedisTemplate.expire(key, expire, TimeUnit.SECONDS);
        }
        return locked;
    }

    /**
     * 尝试获取锁，并至多等待timeout时长
     *
     * @param timeout 超时时长
     * @param unit    时间单位
     * @return 是否获取成功
     * @see [tryLock]
     * @see [lock]
     * @see [unLock]
     */
    boolean tryLock(Long timeout,TimeUnit unit) throws InterruptedException{
        long waitMax = unit.toMillis(timeout);
        long waitAlready = 0;
        while (stringRedisTemplate.opsForValue().setIfAbsent(key, getValue()) != true && waitAlready < waitMax) {
            Thread.sleep(waitMillisPer);
            waitAlready += waitMillisPer;
        }
        log.info(stringRedisTemplate.opsForValue().get(key));
        if (waitAlready < waitMax) {
            stringRedisTemplate.expire(key, expire, TimeUnit.SECONDS);
            return true;
        }
        return false;
    }

    /**
     * 获取锁
     *
     * @see [unLock]
     */
    void lock() throws InterruptedException{
        String uuid = UUID.randomUUID().toString();
        log.info("======>[{}] lock {}", uuid, key);
        long waitMax = TimeUnit.SECONDS.toMillis(safetyTime);
        long waitAlready = 0;
        while (stringRedisTemplate.opsForValue().setIfAbsent(key, getValue()) != true && waitAlready < waitMax) {
            Thread.sleep(waitMillisPer);
            waitAlready += waitMillisPer;
        }
        stringRedisTemplate.opsForValue().set(key, getValue(), expire, TimeUnit.SECONDS);
        log.info("<======[{}] lock {} [{} ms]", uuid, key, waitAlready);
    }

    /**
     * 释放锁
     *
     * @see [lock]
     * @see [tryLock]
     */
    void unLock() {
       if(getValue().equals(stringRedisTemplate.opsForValue().get(key))){
           stringRedisTemplate.delete(key);
           log.info("======>[{}] unlock",key);
       }
    }
}
