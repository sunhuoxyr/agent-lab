package com.sunhuo.agentlab.chat;

import com.sunhuo.agentlab.conversation.Conversation;
import com.sunhuo.agentlab.conversation.ConversationMapper;
import com.sunhuo.agentlab.conversation.Message;
import com.sunhuo.agentlab.conversation.MessageMapper;
import com.sunhuo.agentlab.preset.PromptPresetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

	private final ConversationMapper conversationMapper;
	private final MessageMapper messageMapper;
	private final PromptPresetRepository presetRepository;
	private final LlmClient llmClient;
	private final int historyWindow;

	public ChatService(ConversationMapper conversationMapper,
	                   MessageMapper messageMapper,
	                   PromptPresetRepository presetRepository,
	                   LlmClient llmClient,
	                   @Value("${llm.history-window}") int historyWindow) {
		this.conversationMapper = conversationMapper;
		this.messageMapper = messageMapper;
		this.presetRepository = presetRepository;
		this.llmClient = llmClient;
		this.historyWindow = historyWindow;
	}

	/** 落库用户这一轮；无 conversationId 则先建会话。返回会话 id。 */
	@Transactional
	public Long prepareUserTurn(ChatRequest req) {
		Long conversationId = req.getConversationId();
		if (conversationId == null) {
			Conversation conversation = new Conversation();
			conversation.setTitle(makeTitle(req.getMessage()));
			conversation.setModel(llmClient.model());
			conversationMapper.insert(conversation);
			conversationId = conversation.getId();
		} else if (conversationMapper.findById(conversationId) == null) {
			throw new IllegalArgumentException("会话不存在: " + conversationId);
		}
		Message userMessage = new Message();
		userMessage.setConversationId(conversationId);
		userMessage.setRole("user");
		userMessage.setContent(req.getMessage());
		messageMapper.insert(userMessage);
		return conversationId;
	}

	/** 拼历史 → 调 LLM；流完把整段 assistant 回答落库 */
	public Flux<String> streamReply(Long conversationId, Long presetId) {
		List<Map<String, String>> payload = buildPayload(conversationId, presetId);
		StringBuilder acc = new StringBuilder();
		return llmClient.streamChat(payload)
				.doOnNext(acc::append)
				.doOnComplete(() -> saveAssistantMessage(conversationId, acc.toString()));
	}

	private List<Map<String, String>> buildPayload(Long conversationId, Long presetId) {
		List<Map<String, String>> payload = new ArrayList<>();
		if (presetId != null) {
			presetRepository.findById(presetId).ifPresent(preset ->
					payload.add(Map.of("role", "system", "content", preset.getSystemPrompt())));
		}
		for (Message m : messageMapper.findRecent(conversationId, historyWindow)) {
			payload.add(Map.of("role", m.getRole(), "content", m.getContent()));
		}
		return payload;
	}

	private void saveAssistantMessage(Long conversationId, String content) {
		if (content.isBlank()) {
			return;
		}
		Message assistant = new Message();
		assistant.setConversationId(conversationId);
		assistant.setRole("assistant");
		assistant.setContent(content);
		messageMapper.insert(assistant);
	}

	private String makeTitle(String message) {
		String oneLine = message.strip().replaceAll("\\s+", " ");
		return oneLine.length() > 20 ? oneLine.substring(0, 20) + "…" : oneLine;
	}
}
