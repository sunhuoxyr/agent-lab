package com.sunhuo.agentlab.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/** 固定窗口：INCR 计数，首个请求带 60s 过期；超阈值 429 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private final StringRedisTemplate redis;
	private final int limit;

	public RateLimitInterceptor(StringRedisTemplate redis,
	                            @Value("${ratelimit.chat-per-minute}") int limit) {
		this.redis = redis;
		this.limit = limit;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		String key = "rl:chat:" + request.getRemoteAddr();
		Long count = redis.opsForValue().increment(key);
		if (count != null && count == 1L) {
			redis.expire(key, Duration.ofSeconds(60));
		}
		if (count != null && count > limit) {
			response.setStatus(429);
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().write("{\"message\":\"请求太频繁，稍后再试\"}");
			return false;
		}
		return true;
	}
}
