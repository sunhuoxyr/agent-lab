package com.sunhuo.agentlab.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 知识库 RAG：Obsidian vault 切块 → embedding 存 MySQL（JSON 向量，个人量级暴力余弦够用）
 * → 提问检索 top-k 片段进 system。无 embedding key 时降级关键词重叠。
 * 生产形态会把向量放 Milvus/Chroma 一类向量库——本仓练的是流程不是基础设施。
 */
@Service
public class RagService {

	private static final Logger log = LoggerFactory.getLogger(RagService.class);
	private static final Set<String> SKIP_DIRS = Set.of(
			".obsidian", ".git", ".trash", "Assets", "Excalidraw", "90-launchCenter", "51-丽华");
	private static final int CHUNK_MAX = 800;

	private final DocChunkMapper chunkMapper;
	private final EmbeddingClient embeddingClient;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Path vaultPath;
	private final int topK;

	public RagService(DocChunkMapper chunkMapper,
	                  EmbeddingClient embeddingClient,
	                  @Value("${rag.vault-path}") String vaultPath,
	                  @Value("${rag.top-k}") int topK) {
		this.chunkMapper = chunkMapper;
		this.embeddingClient = embeddingClient;
		this.vaultPath = Path.of(vaultPath);
		this.topK = topK;
	}

	public record ChunkHit(String path, String content, double score) {}
	public record ReindexResult(int files, int chunks, boolean embedded) {}

	/** 全量重建索引（按文件删旧插新） */
	public ReindexResult reindex() {
		List<Path> files = listMarkdown();
		int chunks = 0;
		for (Path file : files) {
			String rel = vaultPath.relativize(file).toString();
			List<String> parts;
			try {
				parts = chunk(Files.readString(file));
			} catch (IOException e) {
				continue; // iCloud 占位等读不到的跳过
			}
			List<float[]> vectors = embeddingClient.embedBatch(parts);
			chunkMapper.deleteByPath(rel);
			for (int i = 0; i < parts.size(); i++) {
				DocChunk c = new DocChunk();
				c.setPath(rel);
				c.setChunkIndex(i);
				c.setContent(parts.get(i));
				c.setVectorJson(vectors.size() == parts.size() ? toJson(vectors.get(i)) : null);
				chunkMapper.insert(c);
				chunks++;
			}
		}
		return new ReindexResult(files.size(), chunks, embeddingClient.available());
	}

	/** 检索 top-k；无向量时全库关键词重叠评分 */
	public List<ChunkHit> search(String query) {
		if (query == null || query.isBlank()) {
			return List.of();
		}
		if (embeddingClient.available()) {
			List<float[]> qv = embeddingClient.embedBatch(List.of(query));
			if (!qv.isEmpty()) {
				return searchByVector(qv.get(0));
			}
		}
		return searchByKeywords(query);
	}

	private List<ChunkHit> searchByVector(float[] queryVector) {
		List<ChunkHit> hits = new ArrayList<>();
		for (DocChunk c : chunkMapper.findAllWithVector()) {
			float[] v = fromJson(c.getVectorJson());
			if (v != null) {
				hits.add(new ChunkHit(c.getPath(), c.getContent(), cosine(queryVector, v)));
			}
		}
		return top(hits);
	}

	private List<ChunkHit> searchByKeywords(String query) {
		Set<String> terms = terms(query);
		List<ChunkHit> hits = new ArrayList<>();
		for (DocChunk c : chunkMapper.findAll()) {
			long matched = terms.stream().filter(t -> c.getContent().contains(t)).count();
			if (matched > 0) {
				hits.add(new ChunkHit(c.getPath(), c.getContent(), (double) matched / terms.size()));
			}
		}
		return top(hits);
	}

	private List<ChunkHit> top(List<ChunkHit> hits) {
		hits.sort(Comparator.comparingDouble(ChunkHit::score).reversed());
		return hits.stream().limit(topK).toList();
	}

	/** 英文按词；中文长串无空格，切二字 shingles（关键词模式的穷人分词） */
	private Set<String> terms(String query) {
		Set<String> terms = new HashSet<>();
		for (String t : query.replaceAll("[^\\p{IsHan}\\p{L}\\p{N}]", " ").split("\\s+")) {
			if (t.matches("\\p{IsHan}{3,}")) {
				for (int i = 0; i < t.length() - 1; i++) {
					terms.add(t.substring(i, i + 2));
				}
			} else if (t.length() >= 2) {
				terms.add(t);
			}
		}
		if (terms.isEmpty() && !query.isBlank()) {
			terms.add(query.trim());
		}
		return terms;
	}

	/** 按二级标题切块，超长硬切 */
	List<String> chunk(String markdown) {
		List<String> parts = new ArrayList<>();
		for (String section : markdown.split("(?m)^(?=## )")) {
			String s = section.strip();
			if (s.isEmpty()) {
				continue;
			}
			for (int i = 0; i < s.length(); i += CHUNK_MAX) {
				parts.add(s.substring(i, Math.min(s.length(), i + CHUNK_MAX)));
			}
		}
		return parts;
	}

	private List<Path> listMarkdown() {
		try (Stream<Path> walk = Files.walk(vaultPath)) {
			return walk.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".md"))
					.filter(this::notSkipped)
					.toList();
		} catch (IOException e) {
			log.warn("扫描 vault 失败: {}", e.getMessage());
			return List.of();
		}
	}

	private boolean notSkipped(Path p) {
		for (Path seg : vaultPath.relativize(p)) {
			if (SKIP_DIRS.contains(seg.toString())) {
				return false;
			}
		}
		return true;
	}

	private double cosine(float[] a, float[] b) {
		double dot = 0, na = 0, nb = 0;
		for (int i = 0; i < Math.min(a.length, b.length); i++) {
			dot += a[i] * b[i];
			na += a[i] * a[i];
			nb += b[i] * b[i];
		}
		return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
	}

	private String toJson(float[] v) {
		try {
			return objectMapper.writeValueAsString(v);
		} catch (Exception e) {
			return null;
		}
	}

	private float[] fromJson(String json) {
		try {
			return objectMapper.readValue(json, float[].class);
		} catch (Exception e) {
			return null;
		}
	}
}
