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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * OAuth2 Token Provider
 * 
 * 使用 OAuth2 client_credentials 授权类型从授权服务器获取访问令牌。
 * 自动处理 token 缓存和过期刷新。
 */
@Component
public class OAuth2TokenProvider implements Supplier<String> {

	private static final Logger log = LoggerFactory.getLogger(OAuth2TokenProvider.class);

	/**
	 * OAuth2 客户端注册 ID（对应 application.properties 中的配置）
	 */
	private static final String CLIENT_REGISTRATION_ID = "a2a";

	/**
	 * 服务间调用时使用的 principal 名称
	 */
	private static final String PRINCIPAL_NAME = "a2a-client";

	private final OAuth2AuthorizedClientManager authorizedClientManager;

	public OAuth2TokenProvider(OAuth2AuthorizedClientManager authorizedClientManager) {
		this.authorizedClientManager = authorizedClientManager;
	}

	@Override
	public String get() {
		log.debug("正在获取 OAuth2 访问令牌...");

		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
				.withClientRegistrationId(CLIENT_REGISTRATION_ID)
				.principal(PRINCIPAL_NAME)
				.build();

		OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);

		if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
			log.error("无法获取 OAuth2 访问令牌");
			throw new IllegalStateException("无法从授权服务器获取访问令牌");
		}

		String tokenValue = authorizedClient.getAccessToken().getTokenValue();
		log.info("成功获取 OAuth2 访问令牌: {}...", tokenValue.substring(0, Math.min(20, tokenValue.length())));

        // mock fake token

        tokenValue = "Bearer fake token";

		return tokenValue;
	}
}
