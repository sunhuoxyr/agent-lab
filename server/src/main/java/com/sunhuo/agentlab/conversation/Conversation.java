package com.sunhuo.agentlab.conversation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Conversation {

	private Long id;
	private String title;
	private String model;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
