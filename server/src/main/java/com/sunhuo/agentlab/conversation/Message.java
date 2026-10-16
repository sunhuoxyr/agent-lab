package com.sunhuo.agentlab.conversation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Message {

	private Long id;
	private Long conversationId;
	private String role;
	private String content;
	private Integer tokensPrompt;
	private Integer tokensCompletion;
	private Integer ttftMs;
	private Boolean stopped = false;
	private LocalDateTime createdAt;
}
