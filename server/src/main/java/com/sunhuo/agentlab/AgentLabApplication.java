package com.sunhuo.agentlab;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sunhuo.agentlab.conversation")
public class AgentLabApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentLabApplication.class, args);
	}
}
