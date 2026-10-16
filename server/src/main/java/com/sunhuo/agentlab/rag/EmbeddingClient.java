package com.sunhuo.agentlab.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 /embeddings 客户端（GLM embedding-3 / 硅基流动 bge 等同一形状）。
 * 未配 key 时不可用——检索降级为关键词重叠，RAG 离线仍可跑。
 */
@Component
public class EmbeddingClient {

	private final WebClient webClient;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String apiKey;
	private final String model;

	public EmbeddingClient(WebClient.Builder builder,
	                       @Value("${rag.embedding-base-url}") String baseUrl,
	                       @Value("${rag.embedding-api-key}") String apiKey,
	                       @Value("${rag.embedding-model}") String model) {
		this.webClient = builder.baseUrl(baseUrl).build();
		this.apiKey = apiKey;
		this.model = model;
	}

	public boolean available() {
		return apiKey != null && !apiKey.isBlank();
	}

	/** 批量 embedding；不可用时返回空列表（调用方降级） */
	public List<float[]> embedBatch(List<String> texts) {
		if (!available() || texts.isEmpty()) {
			return List.of();
		}
		try {
			String resp = webClient.post()
					.uri("/embeddings")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
					.bodyValue(Map.of("model", model, "input", texts))
					.retrieve()
					.bodyToMono(String.class)
					.block(Duration.ofSeconds(60));
			JsonNode data = objectMapper.readTree(resp).path("data");
			List<float[]> vectors = new ArrayList<>();
			for (JsonNode item : data) {
				JsonNode arr = item.path("embedding");
				float[] v = new float[arr.size()];
				for (int i = 0; i < arr.size(); i++) {
					v[i] = (float) arr.get(i).asDouble();
				}
				vectors.add(v);
			}
			return vectors;
		} catch (Exception e) {
			return List.of(); // embedding 服务挂了不拖死聊天，降级关键词
		}
	}
}
