package com.sunhuo.agentlab.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

	/** null → 新会话（首条消息自动生成标题） */
	private Long conversationId;

	/** null → 不带系统提示词 */
	private Long presetId;

	/** true → 先检索知识库片段再回答（需已 reindex） */
	private Boolean rag;

	@NotBlank(message = "消息不能为空")
	private String message;
}
