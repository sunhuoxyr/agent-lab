package com.sunhuo.agentlab.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容流式客户端（DeepSeek / Kimi / GLM 同一形态）。
 * 未配 api-key 时走 mock，骨架离线可跑。
 */
@Component
public class LlmClient {

	private final WebClient webClient;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String apiKey;
	private final String model;

	public LlmClient(WebClient.Builder builder,
	                 @Value("${llm.base-url}") String baseUrl,
	                 @Value("${llm.api-key}") String apiKey,
	                 @Value("${llm.model}") String model) {
		this.webClient = builder.baseUrl(baseUrl).build();
		this.apiKey = apiKey;
		this.model = model;
	}

	public boolean isMock() {
		return apiKey == null || apiKey.isBlank();
	}

	public String model() {
		return isMock() ? "mock-echo" : model;
	}

	/** messages 形如 [{"role":"user","content":"..."}]，流式吐出 delta 文本 */
	public Flux<String> streamChat(List<Map<String, String>> messages) {
		if (isMock()) {
			return mockStream(messages);
		}
		Map<String, Object> body = Map.of(
				"model", model,
				"stream", true,
				"messages", messages
		);
		StringBuilder carry = new StringBuilder();
		return webClient.post()
				.uri("/chat/completions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.bodyValue(body)
				.retrieve()
				.bodyToFlux(String.class)
				.timeout(Duration.ofSeconds(90))
				.concatMap(chunk -> splitSseLines(chunk, carry))
				.takeUntil("[DONE]"::equals)
				.filter(line -> !"[DONE]".equals(line))
				.map(this::extractDelta)
				.filter(delta -> !delta.isEmpty());
	}

	/** SSE 数据块可能跨 chunk，按行切开、残余留到下次 */
	private Flux<String> splitSseLines(String chunk, StringBuilder carry) {
		carry.append(chunk);
		List<String> lines = new ArrayList<>();
		int idx;
		while ((idx = carry.indexOf("\n")) >= 0) {
			String line = carry.substring(0, idx).trim();
			carry.delete(0, idx + 1);
			if (line.startsWith("data:")) {
				lines.add(line.substring(5).trim());
			}
		}
		return Flux.fromIterable(lines);
	}

	private String extractDelta(String data) {
		try {
			JsonNode content = objectMapper.readTree(data)
					.path("choices").path(0).path("delta").path("content");
			return content.isTextual() ? content.asText() : "";
		} catch (Exception e) {
			return "";
		}
	}

	private Flux<String> mockStream(List<Map<String, String>> messages) {
		String last = messages.get(messages.size() - 1).getOrDefault("content", "");
		String reply = "（mock：未配置 LLM_API_KEY）\n\n你说的是：**" + last + "**\n\n配上 key 后，这里流出的就是真实模型回答。";
		return Flux.fromStream(reply.chars().mapToObj(c -> String.valueOf((char) c)))
				.delayElements(Duration.ofMillis(12));
	}
}
