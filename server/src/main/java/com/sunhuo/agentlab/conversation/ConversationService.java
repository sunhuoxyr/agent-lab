package com.sunhuo.agentlab.conversation;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationService {

	private final ConversationMapper conversationMapper;
	private final MessageMapper messageMapper;

	public ConversationService(ConversationMapper conversationMapper, MessageMapper messageMapper) {
		this.conversationMapper = conversationMapper;
		this.messageMapper = messageMapper;
	}

	public List<Conversation> list() {
		return conversationMapper.findAll();
	}

	public List<Message> messages(Long conversationId) {
		return messageMapper.findByConversationId(conversationId);
	}
}
