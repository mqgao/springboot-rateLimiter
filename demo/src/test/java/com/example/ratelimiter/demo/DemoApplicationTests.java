package com.example.ratelimiter.demo;

import com.example.ratelimiter.demo.ratelimiter.RateLimiter;
import com.example.ratelimiter.demo.ratelimiter.RateLimiterFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.TimeUnit;


@RunWith(SpringRunner.class)
@SpringBootTest
public class DemoApplicationTests {
    @Autowired
	private RateLimiterFactory factory;
	@Test
	public void contextLoads() {

		RateLimiter rateLimiter=factory.build("ratelimiter_rest",30L,30);
		try {
			//尝试获取100个令牌，尝试时间2秒
			if(rateLimiter.tryAcquire(100L,2L, TimeUnit.SECONDS)){
				//业务逻辑
			}
		}catch (Exception e){

		}

	}

}
