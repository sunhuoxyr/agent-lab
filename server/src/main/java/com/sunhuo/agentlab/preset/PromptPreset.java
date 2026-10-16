package com.sunhuo.agentlab.preset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "prompt_preset")
public class PromptPreset {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@Column(name = "system_prompt", columnDefinition = "TEXT")
	private String systemPrompt;

	private LocalDateTime createdAt;

	@PrePersist
	void prePersist() {
		this.createdAt = LocalDateTime.now();
	}
}
