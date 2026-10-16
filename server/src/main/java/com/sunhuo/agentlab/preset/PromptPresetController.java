package com.sunhuo.agentlab.preset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/presets")
public class PromptPresetController {

	private final PromptPresetRepository repository;

	public PromptPresetController(PromptPresetRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	public List<PromptPreset> list() {
		return repository.findAll();
	}

	@PostMapping
	public PromptPreset create(@Valid @RequestBody CreatePresetRequest req) {
		PromptPreset preset = new PromptPreset();
		preset.setName(req.getName());
		preset.setSystemPrompt(req.getSystemPrompt());
		return repository.save(preset);
	}

	@Data
	public static class CreatePresetRequest {
		@NotBlank(message = "名称不能为空")
		private String name;
		@NotBlank(message = "提示词不能为空")
		private String systemPrompt;
	}
}
