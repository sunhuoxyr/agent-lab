package com.sunhuo.agentlab.conversation;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper {

	@Insert("INSERT INTO message(conversation_id, role, content) VALUES(#{conversationId}, #{role}, #{content})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(Message message);

	@Select("SELECT * FROM message WHERE conversation_id = #{conversationId} ORDER BY id")
	List<Message> findByConversationId(Long conversationId);

	/** 最近 N 条（保持正序），作 LLM 历史窗 */
	@Select("SELECT * FROM (SELECT * FROM message WHERE conversation_id = #{conversationId} ORDER BY id DESC LIMIT #{limit}) t ORDER BY id")
	List<Message> findRecent(@Param("conversationId") Long conversationId, @Param("limit") int limit);
}
