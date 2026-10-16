package com.sunhuo.agentlab.conversation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

	private final ConversationService conversationService;

	public ConversationController(ConversationService conversationService) {
		this.conversationService = conversationService;
	}

	@GetMapping
	public List<Conversation> list() {
		return conversationService.list();
	}

	@GetMapping("/{id}/messages")
	public List<Message> messages(@PathVariable Long id) {
		return conversationService.messages(id);
	}
}
