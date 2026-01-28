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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A2A 安全客户端示例
 *
 * 功能说明：
 * 1. 通过 AgentCardProvider 从 Nacos 自动发现 Agent
 * 2. 使用 tokenProvider 注入 OAuth2 Token
 * 3. 实际调用受保护的 A2A 服务
 *
 * 运行方式：
 * 1. 先启动 a2a-secure-server
 * 2. 然后运行此客户端：mvn spring-boot:run
 * 3. 访问 http://localhost:18081/invoke?message=你好 测试
 */
@SpringBootApplication
public class SecureClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(SecureClientApplication.class, args);
	}
}
