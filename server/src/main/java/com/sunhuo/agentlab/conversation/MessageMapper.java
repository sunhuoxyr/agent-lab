package com.sunhuo.agentlab.conversation;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper {

	@Insert("INSERT INTO message(conversation_id, role, content, tokens_prompt, tokens_completion, ttft_ms) "
			+ "VALUES(#{conversationId}, #{role}, #{content}, #{tokensPrompt}, #{tokensCompletion}, #{ttftMs})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(Message message);

	@Select("SELECT * FROM message WHERE conversation_id = #{conversationId} ORDER BY id")
	List<Message> findByConversationId(Long conversationId);

	/** 最近 N 条（保持正序），作 LLM 历史窗 */
	@Select("SELECT * FROM (SELECT * FROM message WHERE conversation_id = #{conversationId} ORDER BY id DESC LIMIT #{limit}) t ORDER BY id")
	List<Message> findRecent(@Param("conversationId") Long conversationId, @Param("limit") int limit);

	@Select("SELECT COUNT(*) AS messageCount, COALESCE(SUM(tokens_prompt),0) AS promptTokens, "
			+ "COALESCE(SUM(tokens_completion),0) AS completionTokens, COALESCE(AVG(ttft_ms),0) AS avgTtftMs "
			+ "FROM message WHERE role = 'assistant'")
	java.util.Map<String, Object> assistantStats();
}
