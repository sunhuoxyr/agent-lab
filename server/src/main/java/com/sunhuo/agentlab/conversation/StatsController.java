package com.sunhuo.agentlab.conversation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

	private final MessageMapper messageMapper;

	public StatsController(MessageMapper messageMapper) {
		this.messageMapper = messageMapper;
	}

	/** 数字叙事种子：assistant 消息数、token 累计、平均首 token 延迟 */
	@GetMapping
	public Map<String, Object> stats() {
		return messageMapper.assistantStats();
	}
}
