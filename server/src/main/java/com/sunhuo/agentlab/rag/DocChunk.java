package com.sunhuo.agentlab.rag;

import lombok.Data;

@Data
public class DocChunk {

	private Long id;
	private String path;
	private Integer chunkIndex;
	private String content;
	private String vectorJson;
}
