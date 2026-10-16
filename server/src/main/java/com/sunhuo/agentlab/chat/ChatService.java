package com.sunhuo.agentlab.chat;

import com.sunhuo.agentlab.conversation.Conversation;
import com.sunhuo.agentlab.conversation.ConversationMapper;
import com.sunhuo.agentlab.conversation.Message;
import com.sunhuo.agentlab.conversation.MessageMapper;
import com.sunhuo.agentlab.preset.PromptPresetRepository;
import com.sunhuo.agentlab.rag.RagService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ChatService {

	private final ConversationMapper conversationMapper;
	private final MessageMapper messageMapper;
	private final PromptPresetRepository presetRepository;
	private final LlmClient llmClient;
	private final RagService ragService;
	private final int historyWindow;

	public ChatService(ConversationMapper conversationMapper,
	                   MessageMapper messageMapper,
	                   PromptPresetRepository presetRepository,
	                   LlmClient llmClient,
	                   RagService ragService,
	                   @Value("${llm.history-window}") int historyWindow) {
		this.conversationMapper = conversationMapper;
		this.messageMapper = messageMapper;
		this.presetRepository = presetRepository;
		this.llmClient = llmClient;
		this.ragService = ragService;
		this.historyWindow = historyWindow;
	}

	/** 一条助手回复的流 + 本次 RAG 命中的来源（未开 RAG 为空表） */
	public record ReplyStream(Flux<String> flux, List<String> sources) {}

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

	/** 拼历史（按需叠 RAG 片段）→ 调 LLM；流完把整段 assistant 回答带指标落库 */
	public ReplyStream streamReply(Long conversationId, Long presetId, boolean ragEnabled) {
		List<Map<String, String>> payload = new ArrayList<>();
		List<String> sources = new ArrayList<>();
		if (presetId != null) {
			presetRepository.findById(presetId).ifPresent(preset ->
					payload.add(Map.of("role", "system", "content", preset.getSystemPrompt())));
		}
		List<Message> history = messageMapper.findRecent(conversationId, historyWindow);
		if (ragEnabled && !history.isEmpty()) {
			String query = history.get(history.size() - 1).getContent();
			List<RagService.ChunkHit> hits = ragService.search(query);
			if (!hits.isEmpty()) {
				StringBuilder kb = new StringBuilder("以下知识库片段供参考，回答后附出来源路径：\n\n");
				for (RagService.ChunkHit hit : hits) {
					kb.append("【").append(hit.path()).append("】\n").append(hit.content()).append("\n\n");
				}
				sources.addAll(hits.stream().map(RagService.ChunkHit::path).distinct().toList());
				payload.add(Map.of("role", "system", "content", kb.toString()));
			}
		}
		for (Message m : history) {
			payload.add(Map.of("role", m.getRole(), "content", m.getContent()));
		}

		StringBuilder acc = new StringBuilder();
		AtomicLong startedAt = new AtomicLong();
		AtomicLong ttftMs = new AtomicLong(-1);
		AtomicInteger promptTokens = new AtomicInteger();
		AtomicInteger completionTokens = new AtomicInteger();
		// complete 与 cancel 两路径都写库，CAS 保证只落一次（断线时两回调可能并发触发）
		java.util.concurrent.atomic.AtomicBoolean savedOnce = new java.util.concurrent.atomic.AtomicBoolean();

		Flux<String> flux = Flux.defer(() -> {
					startedAt.set(System.currentTimeMillis());
					return llmClient.streamChat(payload, (pt, ct) -> {
						promptTokens.set(pt);
						completionTokens.set(ct);
					});
				})
				.doOnNext(token -> ttftMs.compareAndSet(-1, System.currentTimeMillis() - startedAt.get()))
				.doOnNext(acc::append)
				.doOnComplete(() -> {
					if (savedOnce.compareAndSet(false, true)) {
						saveAssistantMessage(conversationId, acc.toString(),
								promptTokens.get(), completionTokens.get(), ttftMs.get(), false);
					}
				})
				// Abort/断线：完整回调不触发，半截入库并标 stopped（五态对齐；正常完成后 dispose 不会误触）
				.doOnCancel(() -> {
					if (savedOnce.compareAndSet(false, true)) {
						saveAssistantMessage(conversationId, acc.toString(),
								promptTokens.get(), completionTokens.get(), ttftMs.get(), true);
					}
				});
		return new ReplyStream(flux, sources);
	}

	private void saveAssistantMessage(Long conversationId, String content,
	                                  int promptTokens, int completionTokens, long ttftMs, boolean stopped) {
		if (content.isBlank()) {
			return;
		}
		Message assistant = new Message();
		assistant.setConversationId(conversationId);
		assistant.setRole("assistant");
		assistant.setContent(content);
		assistant.setTokensPrompt(promptTokens);
		assistant.setTokensCompletion(completionTokens);
		assistant.setTtftMs(ttftMs >= 0 ? (int) ttftMs : null);
		assistant.setStopped(stopped);
		messageMapper.insert(assistant);
	}

	private String makeTitle(String message) {
		String oneLine = message.strip().replaceAll("\\s+", " ");
		return oneLine.length() > 20 ? oneLine.substring(0, 20) + "…" : oneLine;
	}
}
