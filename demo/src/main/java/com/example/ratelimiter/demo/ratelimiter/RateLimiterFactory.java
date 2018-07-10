package com.example.ratelimiter.demo.ratelimiter;
/*
 * Created by gao.mq on 2018-06-26
 * desc：RateLimiter工厂类
 */

import lombok.Synchronized;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RateLimiterFactory {

    @Autowired
    private SyncLockFactory syncLockFactory;

    @Autowired
    private RedisTemplate<String, RedisPermits> redisTemplate;

    private Map<String,RateLimiter> rateLimiterMap =new HashMap<>();

    /**
     * 创建RateLimiter
     *
     * @param key Redis key
     * @param permitsPerSecond 每秒放入的令牌数
     * @param maxBurstSeconds 最大存储maxBurstSeconds秒生成的令牌
     *
     * @return RateLimiter
     */
    @Synchronized
   public RateLimiter build(String key,Long permitsPerSecond, int maxBurstSeconds){
        if(permitsPerSecond==null){
            permitsPerSecond=60L;
        }
        if (!rateLimiterMap.containsKey(key)) {
            RateLimiter rateLimiter=new RateLimiter(key, permitsPerSecond, maxBurstSeconds, syncLockFactory.build(key+"_lock",null,null),redisTemplate);
            rateLimiterMap.put(key,rateLimiter);
        }
        return rateLimiterMap.get(key);
    }
}
