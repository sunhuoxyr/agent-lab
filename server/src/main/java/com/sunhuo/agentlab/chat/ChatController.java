package com.sunhuo.agentlab.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final ChatService chatService;
	private final ObjectMapper objectMapper;

	public ChatController(ChatService chatService, ObjectMapper objectMapper) {
		this.chatService = chatService;
		this.objectMapper = objectMapper;
	}

	/** 非流式一口价：给 n8n 等自动化调（SSE 对 HTTP 节点不友好）。MVC 线程内 block，个人工具可接受 */
	@PostMapping("/once")
	public Map<String, Object> once(@Validated @RequestBody ChatRequest req) {
		Long conversationId = chatService.prepareUserTurn(req);
		String reply = chatService.streamReply(conversationId, req.getPresetId())
				.collectList()
				.map(parts -> String.join("", parts))
				.block(java.time.Duration.ofSeconds(95));
		return Map.of("conversationId", conversationId, "reply", reply == null ? "" : reply);
	}

	@PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@Validated @RequestBody ChatRequest req) {
		Long conversationId = chatService.prepareUserTurn(req);

		SseEmitter emitter = new SseEmitter(120_000L);
		send(emitter, "meta", "{\"conversationId\":" + conversationId + "}");

		chatService.streamReply(conversationId, req.getPresetId())
				.subscribe(
						token -> send(emitter, "token", toJson(token)),
						error -> {
							send(emitter, "error", toJson(String.valueOf(error.getMessage())));
							emitter.complete();
						},
						emitter::complete
				);
		return emitter;
	}

	/** token 统一 JSON 编码成单行：SSE data 里裸换行会把事件切碎 */
	private String toJson(String text) {
		try {
			return objectMapper.writeValueAsString(text);
		} catch (Exception e) {
			return "\"\"";
		}
	}

	private void send(SseEmitter emitter, String event, String data) {
		try {
			emitter.send(SseEmitter.event().name(event).data(data));
		} catch (IOException | IllegalStateException e) {
			emitter.complete();
		}
	}
}
