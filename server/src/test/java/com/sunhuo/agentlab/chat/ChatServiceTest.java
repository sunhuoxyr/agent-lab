package com.sunhuo.agentlab.chat;

import com.sunhuo.agentlab.conversation.ConversationMapper;
import com.sunhuo.agentlab.conversation.Message;
import com.sunhuo.agentlab.conversation.MessageMapper;
import com.sunhuo.agentlab.preset.PromptPreset;
import com.sunhuo.agentlab.preset.PromptPresetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 拼历史窗：预设在首、历史其后、窗口透传、assistant 落库 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

	@Mock
	ConversationMapper conversationMapper;
	@Mock
	MessageMapper messageMapper;
	@Mock
	PromptPresetRepository presetRepository;
	@Mock
	LlmClient llmClient;

	private ChatService newService(int historyWindow) {
		return new ChatService(conversationMapper, messageMapper, presetRepository, llmClient, historyWindow);
	}

	@Test
	@DisplayName("payload：system 预设在最前，历史按序在后，窗口值透传给 mapper")
	void payloadStartsWithPresetThenHistory() {
		ChatService service = newService(20);

		PromptPreset preset = new PromptPreset();
		preset.setSystemPrompt("你是代码助手");
		when(presetRepository.findById(7L)).thenReturn(Optional.of(preset));
		when(messageMapper.findRecent(1L, 20)).thenReturn(List.of(msg("user", "第一问"), msg("assistant", "第一答")));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Map<String, String>>> payloadCaptor = ArgumentCaptor.forClass(List.class);
		when(llmClient.streamChat(payloadCaptor.capture())).thenReturn(Flux.just("你", "好"));

		String reply = service.streamReply(1L, 7L).collectList().block()
				.stream().reduce("", String::concat);

		List<Map<String, String>> payload = payloadCaptor.getValue();
		assertThat(payload).hasSize(3);
		assertThat(payload.get(0)).containsEntry("role", "system").containsEntry("content", "你是代码助手");
		assertThat(payload.get(1)).containsEntry("role", "user").containsEntry("content", "第一问");
		assertThat(payload.get(2)).containsEntry("role", "assistant").containsEntry("content", "第一答");
		assertThat(reply).isEqualTo("你好");
		verify(messageMapper).findRecent(1L, 20);
		verify(messageMapper).insert(any(Message.class)); // 流完 assistant 落库
	}

	@Test
	@DisplayName("payload：无预设时不带 system，空回答不落库")
	void noPresetNoSystemMessage() {
		ChatService service = newService(20);
		when(messageMapper.findRecent(1L, 20)).thenReturn(List.of(msg("user", "你好")));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Map<String, String>>> payloadCaptor = ArgumentCaptor.forClass(List.class);
		when(llmClient.streamChat(payloadCaptor.capture())).thenReturn(Flux.just(""));

		service.streamReply(1L, null).collectList().block();

		List<Map<String, String>> payload = payloadCaptor.getValue();
		assertThat(payload).hasSize(1);
		assertThat(payload.get(0)).containsEntry("role", "user");
	}

	private Message msg(String role, String content) {
		Message m = new Message();
		m.setRole(role);
		m.setContent(content);
		return m;
	}
}
