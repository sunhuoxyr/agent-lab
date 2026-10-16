package com.sunhuo.agentlab.rag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

	private final RagService ragService;

	public RagController(RagService ragService) {
		this.ragService = ragService;
	}

	/** 全量重建索引（个人量级同步跑；大了再改异步） */
	@PostMapping("/reindex")
	public RagService.ReindexResult reindex() {
		return ragService.reindex();
	}
}
