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

package com.alibaba.cloud.ai.examples.a2a.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OAuth2 授权服务器
 * 
 * 功能说明：
 * 1. 提供 OAuth2 授权服务，支持 client_credentials 授权类型
 * 2. 颁发 JWT Token 供 A2A 通信使用
 * 3. 提供 JWKS 端点供资源服务器验证 Token
 * 
 * 运行方式：
 * 1. 启动应用：mvn spring-boot:run
 * 2. 服务端默认运行在 http://localhost:9000
 * 
 * 测试获取 Token:
 * curl -X POST http://localhost:9000/oauth2/token \
 *   -d "grant_type=client_credentials&scope=agent.call" \
 *   -u "a2a-client:a2a-secret"
 */
@SpringBootApplication
public class AuthServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServerApplication.class, args);
	}
}
