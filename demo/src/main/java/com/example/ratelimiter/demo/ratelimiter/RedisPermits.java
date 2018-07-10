package com.example.ratelimiter.demo.ratelimiter;/*
 * Created by gao.mq on 2018-06-26
 * desc：令牌桶数据模型
 */

import lombok.Data;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.lang.Math.min;

@Data
public class RedisPermits implements Serializable{
    private static final long serialVersionUID = 10000232301L;

    public Long maxPermits;  //最大存储令牌数
    public Long storedPermits; //当前存储令牌数
    public Long intervalMillis; //添加令牌时间间隔
    public Long nextFreeTicketMillis= System.currentTimeMillis(); //下次请求可以获取令牌的起始时间，默认当前系统时间

    /**
     * 构建Redis令牌数据模型
     *
     * @param permitsPerSecond 每秒放入的令牌数
     * @param maxBurstSeconds maxPermits由此字段计算，最大存储maxBurstSeconds秒生成的令牌 60
     * @param nextFreeTicketMillis 下次请求可以获取令牌的起始时间，默认当前系统时间
     */
    RedisPermits(Long permitsPerSecond, int maxBurstSeconds, Long nextFreeTicketMillis){
        if(maxBurstSeconds==0){
            maxBurstSeconds=60;
        }
        if(nextFreeTicketMillis==null){
            nextFreeTicketMillis=System.currentTimeMillis();
        }
        this.maxPermits=permitsPerSecond * maxBurstSeconds;
        this.storedPermits=new Long(permitsPerSecond);
        this.intervalMillis=TimeUnit.SECONDS.toMillis(1) / permitsPerSecond;
        this.nextFreeTicketMillis=nextFreeTicketMillis;
    }
    RedisPermits(){ }

    /**
     * 计算redis-key过期时长（秒）
     *
     * @return redis-key过期时长（秒）
     */
    public Long expires() {
        long now = System.currentTimeMillis();
        return 2 * TimeUnit.MINUTES.toSeconds(1) + TimeUnit.MILLISECONDS.toSeconds(max(nextFreeTicketMillis, now) - now);
    }

    /**
     * if nextFreeTicket is in the past, reSync to now
     * 若当前时间晚于nextFreeTicketMicros，则计算该段时间内可以生成多少令牌，将生成的令牌加入令牌桶中并更新数据
     *
     * @return 是否更新
     */
    public boolean reSync(Long now){
        if (now > nextFreeTicketMillis) {
            storedPermits = min(maxPermits, storedPermits + (now - nextFreeTicketMillis) / intervalMillis);
            nextFreeTicketMillis = now;
            return true;
        }
        return false;
    }


}
