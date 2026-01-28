/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.examples.a2a.server;

import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A2A 安全服务端示例
 *
 * 功能说明：
 * 1. 暴露一个 ReactAgent 作为 A2A 服务
 * 2. 使用 OAuth2 Resource Server 保护 A2A 端点
 * 3. 只有携带有效 JWT Token 的请求才能访问
 *
 * 运行方式：
 * 1. 确保 a2a-auth-server 已启动在 localhost:9000
 * 2. 配置 DashScope API Key: spring.ai.dashscope.api-key=xxx
 * 3. 启动应用：mvn spring-boot:run
 * 4. 服务端默认运行在 http://localhost:18080
 */
@SpringBootApplication
@EnableWebSecurity
public class SecureServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SecureServerApplication.class, args);
	}

	/**
	 * 定义要暴露的 Agent
	 * 这个 Agent 会被 A2A 框架自动注册到端点
	 */
	@Bean
	public Agent rootAgent(ChatModel chatModel) {
		return ReactAgent.builder()
				.name("SecureDataAnalysisAgent")
				.model(chatModel)
				.description("一个受 OAuth2 保护的数据分析 Agent")
				.instruction("你是一个专业的数据分析专家，帮助用户进行数据统计和分析。")
				.outputKey("messages")
				.build();
	}

	/**
	 * 安全配置
	 * - /.well-known/agent.json 公开访问（用于发现 Agent）
	 * - 其他所有请求需要认证
	 * 
	 * JWT 验证通过 spring.security.oauth2.resourceserver.jwt.issuer-uri 自动配置
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
					.requestMatchers("/.well-known/agent.json").permitAll()
					.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
		return http.build();
	}
}

