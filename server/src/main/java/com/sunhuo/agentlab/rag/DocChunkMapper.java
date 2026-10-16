package com.sunhuo.agentlab.rag;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocChunkMapper {

	@Insert("INSERT INTO doc_chunk(path, chunk_index, content, vector_json) VALUES(#{path}, #{chunkIndex}, #{content}, #{vectorJson})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(DocChunk chunk);

	@Delete("DELETE FROM doc_chunk WHERE path = #{path}")
	int deleteByPath(String path);

	@Select("SELECT * FROM doc_chunk WHERE vector_json IS NOT NULL")
	List<DocChunk> findAllWithVector();

	@Select("SELECT * FROM doc_chunk")
	List<DocChunk> findAll();

	@Select("SELECT COUNT(*) FROM doc_chunk")
	int count();
}
