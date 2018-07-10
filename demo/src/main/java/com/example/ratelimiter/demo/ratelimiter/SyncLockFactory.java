package com.example.ratelimiter.demo.ratelimiter;

import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/*
 * Created by gao.mq on 2018-06-26
 * desc：SyncLock同步锁工厂类
 */
@Slf4j
@Component
public class SyncLockFactory {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Map<String, SyncLock> syncLockMap = new HashMap<>();

    /**
     * 创建SyncLock
     *
     * @param key        Redis key
     * @param expire     Redis TTL/秒，默认10秒
     * @param safetyTime 安全时间/秒，为了防止程序异常导致死锁，在此时间后强制拿锁，默认 expire * 5 秒
     */
    @Synchronized
    SyncLock build(String key, Long expire, Long safetyTime) {
        if (expire == null) {
            expire = 10L;
        }
        if(safetyTime==null){
            safetyTime=expire*5;
        }
        if (!syncLockMap.containsKey(key)) {
            syncLockMap.put(key, new SyncLock(key, stringRedisTemplate, expire, safetyTime));
        }
        return syncLockMap.get(key);
    }

}
