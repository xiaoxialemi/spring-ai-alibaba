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

package com.alibaba.cloud.ai.examples.a2a.client;

import com.alibaba.cloud.ai.graph.agent.a2a.A2aRemoteAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
public class SecureClientController {

	private static final Logger log = LoggerFactory.getLogger(SecureClientController.class);

	/**
	 * 要调用的远程 Agent 名称（与服务端注册的名称一致）
	 */
	private static final String REMOTE_AGENT_NAME = "SecureDataAnalysisAgent";

	private final AgentCardProvider agentCardProvider;
	private final OAuth2TokenProvider tokenProvider;

	@Autowired
	public SecureClientController(AgentCardProvider agentCardProvider, OAuth2TokenProvider tokenProvider) {
		this.agentCardProvider = agentCardProvider;
		this.tokenProvider = tokenProvider;
	}

	@GetMapping("/invoke")
	public Flux<String> invoke(@RequestParam(defaultValue = "请分析一下最近一周的销售数据趋势") String message) {
		log.info("收到调用请求: {}", message);

		try {
			log.info("AgentCardProvider 类型: {}", agentCardProvider.getClass().getSimpleName());

			// 通过 AgentCardProvider 从 Nacos 发现 Agent 并创建 A2aRemoteAgent
			A2aRemoteAgent remoteAgent = A2aRemoteAgent.builder()
					.name(REMOTE_AGENT_NAME)
					.description("连接到受保护的数据分析 Agent")
					// ⭐ 使用 AgentCardProvider 从 Nacos 自动获取 AgentCard
					.agentCardProvider(agentCardProvider)
					// ⭐ 核心功能：使用 OAuth2TokenProvider 动态获取 Token
					.tokenProvider(() -> {
						String token = tokenProvider.get();
						log.info("TokenProvider 被调用，注入 Token: Bearer {}...", 
								token.substring(0, Math.min(20, token.length())));
						return token;
					})
					.instruction("{input}")
					.build();

			// 异步流式调用
			return remoteAgent.getAndCompileGraph().stream(Map.of("input", message)).map(r -> {
						Object output = r.state().value("output").orElse(r.state().value("messages").orElse(null));
						return output != null ? output.toString() : "";
					})
				.doOnComplete(() -> log.info("调用完成"))
				.doOnError(e -> log.error("调用出错", e));

		} catch (Exception e) {
			log.error("创建 Agent 失败", e);
			return Flux.error(e);
		}
	}
}

