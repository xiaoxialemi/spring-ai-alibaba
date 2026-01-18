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

package com.alibaba.cloud.ai.a2a.registry.nacos.discovery;

import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.nacos.common.utils.CollectionUtils;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring AI Alibaba Agent Card Wrapper for Nacos with weighted load balancing
 * support.
 * <p>
 * This wrapper uses smooth weighted round-robin algorithm to distribute traffic
 * among multiple agent instances according to their configured weights.
 * </p>
 * <p>
 * Weights can be configured in Nacos console by adding a "weight" field to the
 * AgentInterface metadata. If no weight is specified, a default weight of 1 is
 * used.
 * </p>
 *
 * @author xiweng.yy
 */
public class NacosAgentCardWrapper extends AgentCardWrapper {

	private static final Logger LOGGER = LoggerFactory.getLogger(NacosAgentCardWrapper.class);

	/**
	 * The key used to extract weight from AgentInterface URL parameters or
	 * metadata.
	 */
	public static final String WEIGHT_KEY = "weight";

	/**
	 * Weighted load balancer for smooth weighted round-robin selection.
	 */
	private volatile WeightedLoadBalancer loadBalancer;

	public NacosAgentCardWrapper(AgentCard agentCard) {
		super(agentCard);
		this.loadBalancer = buildLoadBalancer(agentCard);
	}

	/**
	 * Build a WeightedLoadBalancer from the AgentCard's additional interfaces.
	 *
	 * @param agentCard the agent card
	 * @return the weighted load balancer
	 */
	private WeightedLoadBalancer buildLoadBalancer(AgentCard agentCard) {
		if (CollectionUtils.isEmpty(agentCard.additionalInterfaces())) {
			return new WeightedLoadBalancer();
		}

		String preferredTransport = agentCard.preferredTransport();
		List<WeightedAgentInterface> weightedInterfaces = new ArrayList<>();

		for (AgentInterface iface : agentCard.additionalInterfaces()) {
			// Filter by preferred transport
			if (preferredTransport != null && !preferredTransport.equals(iface.transport())) {
				continue;
			}

			int weight = extractWeight(iface);
			weightedInterfaces.add(new WeightedAgentInterface(iface.url(), iface.transport(), weight));

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Added weighted interface: url={}, transport={}, weight={}",
						iface.url(), iface.transport(), weight);
			}
		}

		return new WeightedLoadBalancer(weightedInterfaces);
	}

	/**
	 * Extract weight from AgentInterface.
	 * <p>
	 * Weight can be specified in the URL as a query parameter:
	 * http://host:port?weight=5
	 * If not specified, returns the default weight of 1.
	 * </p>
	 *
	 * @param iface the agent interface
	 * @return the weight value
	 */
	private int extractWeight(AgentInterface iface) {
		String url = iface.url();
		if (url == null || url.isEmpty()) {
			return WeightedAgentInterface.DEFAULT_WEIGHT;
		}

		// Try to parse weight from URL query parameter
		try {
			int queryIndex = url.indexOf('?');
			if (queryIndex != -1) {
				String queryString = url.substring(queryIndex + 1);
				String[] params = queryString.split("&");
				for (String param : params) {
					String[] keyValue = param.split("=");
					if (keyValue.length == 2 && WEIGHT_KEY.equals(keyValue[0])) {
						return Integer.parseInt(keyValue[1]);
					}
				}
			}
		} catch (NumberFormatException e) {
			LOGGER.warn("Failed to parse weight from URL: {}, using default weight", url);
		}

		return WeightedAgentInterface.DEFAULT_WEIGHT;
	}

	@Override
	public String url() {
		if (loadBalancer == null || loadBalancer.isEmpty()) {
			return super.url();
		}

		String selectedUrl = loadBalancer.selectUrl();
		if (selectedUrl == null) {
			return super.url();
		}

		// Remove weight parameter from URL before returning
		return removeWeightParameter(selectedUrl);
	}

	/**
	 * Remove the weight query parameter from URL.
	 *
	 * @param url the URL with potential weight parameter
	 * @return the URL without weight parameter
	 */
	private String removeWeightParameter(String url) {
		if (url == null || !url.contains(WEIGHT_KEY + "=")) {
			return url;
		}

		try {
			int queryIndex = url.indexOf('?');
			if (queryIndex == -1) {
				return url;
			}

			String baseUrl = url.substring(0, queryIndex);
			String queryString = url.substring(queryIndex + 1);
			String[] params = queryString.split("&");

			StringBuilder newQuery = new StringBuilder();
			for (String param : params) {
				if (!param.startsWith(WEIGHT_KEY + "=")) {
					if (newQuery.length() > 0) {
						newQuery.append("&");
					}
					newQuery.append(param);
				}
			}

			if (newQuery.length() == 0) {
				return baseUrl;
			}
			return baseUrl + "?" + newQuery;
		} catch (Exception e) {
			return url;
		}
	}

	@Override
	public void setAgentCard(AgentCard agentCard) {
		super.setAgentCard(agentCard);
		// Rebuild load balancer when agent card is updated
		this.loadBalancer = buildLoadBalancer(agentCard);
	}
}
