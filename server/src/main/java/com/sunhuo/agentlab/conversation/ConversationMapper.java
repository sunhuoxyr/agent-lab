package com.sunhuo.agentlab.conversation;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ConversationMapper {

	@Insert("INSERT INTO conversation(title, model) VALUES(#{title}, #{model})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(Conversation conversation);

	@Select("SELECT * FROM conversation ORDER BY updated_at DESC, id DESC")
	List<Conversation> findAll();

	@Select("SELECT * FROM conversation WHERE id = #{id}")
	Conversation findById(Long id);

	@Update("UPDATE conversation SET title = #{title} WHERE id = #{id}")
	int updateTitle(@Param("id") Long id, @Param("title") String title);
}
