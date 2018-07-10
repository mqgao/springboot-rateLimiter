package com.example.ratelimiter.demo.configer;

import com.example.ratelimiter.demo.ratelimiter.RedisPermits;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

/**
 * redis配置
 * 
 * @author gao.mq
 */

@Configuration
public class RedisConfig extends CachingConfigurerSupport {


	@Bean
	public RedisTemplate<String, RedisPermits> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, RedisPermits> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
		template.setDefaultSerializer(serializer);
		return template;
	}

}