package com.sunhuo.agentlab.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 固定窗口限流：首请求放行并设过期，超阈值 429 */
@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

	@Mock
	StringRedisTemplate redis;
	@Mock
	ValueOperations<String, String> ops;
	@Mock
	HttpServletRequest request;
	@Mock
	HttpServletResponse response;

	@BeforeEach
	void setUp() {
		when(redis.opsForValue()).thenReturn(ops);
		when(request.getRemoteAddr()).thenReturn("127.0.0.1");
	}

	@Test
	@DisplayName("窗口内首请求：放行，并给 key 设 60s 过期")
	void firstRequestPassesAndSetsExpiry() throws Exception {
		when(ops.increment("rl:chat:127.0.0.1")).thenReturn(1L);
		RateLimitInterceptor interceptor = new RateLimitInterceptor(redis, 20);

		boolean allowed = interceptor.preHandle(request, response, new Object());

		assertThat(allowed).isTrue();
		verify(redis).expire(eq("rl:chat:127.0.0.1"), any(Duration.class));
	}

	@Test
	@DisplayName("第 21 次请求：拒绝并写 429")
	void overLimitRejectedWith429() throws Exception {
		when(ops.increment("rl:chat:127.0.0.1")).thenReturn(21L);
		StringWriter body = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(body));
		RateLimitInterceptor interceptor = new RateLimitInterceptor(redis, 20);

		boolean allowed = interceptor.preHandle(request, response, new Object());

		assertThat(allowed).isFalse();
		verify(response).setStatus(429);
		assertThat(body.toString()).contains("频繁");
	}
}
