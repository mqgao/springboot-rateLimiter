# 说明
 基于Guava的RateLimiter、Redis实现分布式锁进行并发控制、限流管理功能，使用springboo实现分布式下的高可用
# 使用方法 rest接口
RateLimiter restRateLimiter = rateLimiterFactory.build("ratelimiter:im:rest", 9000 /30, 30)
### 获取令牌 
##### 尝试在两秒钟的时间获取msgs.size个令牌
restRateLimiter.tryAcquire(msgs.size, 2, TimeUnit.SECONDS)
