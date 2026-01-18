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

import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentInterface;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NacosAgentCardWrapper with weighted load balancing.
 */
class NacosAgentCardWrapperTest {

    @Test
    void testWeightExtraction_FromUrlParameter() {
        AgentCard agentCard = createMockAgentCard(
                "http://default:8080",
                "http",
                Arrays.asList(
                        new AgentInterface("http", "http://host1:8080?weight=5"),
                        new AgentInterface("http", "http://host2:8080?weight=3")));

        NacosAgentCardWrapper wrapper = new NacosAgentCardWrapper(agentCard);

        // Run multiple selections and count distribution
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 80; i++) {
            String url = wrapper.url();
            // URL should have weight parameter removed
            assertFalse(url.contains("weight="), "Weight parameter should be removed from URL");
            counts.merge(url, 1, Integer::sum);
        }

        // 5:3 ratio means host1=50, host2=30 in 80 calls
        assertEquals(50, counts.getOrDefault("http://host1:8080", 0), "Host1 (weight=5) should be selected 50 times");
        assertEquals(30, counts.getOrDefault("http://host2:8080", 0), "Host2 (weight=3) should be selected 30 times");
    }

    @Test
    void testDefaultWeight_WhenNoWeightSpecified() {
        AgentCard agentCard = createMockAgentCard(
                "http://default:8080",
                "http",
                Arrays.asList(
                        new AgentInterface("http", "http://host1:8080"),
                        new AgentInterface("http", "http://host2:8080")));

        NacosAgentCardWrapper wrapper = new NacosAgentCardWrapper(agentCard);

        // With default weights (1:1), should be evenly distributed
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            String url = wrapper.url();
            counts.merge(url, 1, Integer::sum);
        }

        assertEquals(50, counts.getOrDefault("http://host1:8080", 0));
        assertEquals(50, counts.getOrDefault("http://host2:8080", 0));
    }

    @Test
    void testFallbackToDefaultUrl_WhenNoAdditionalInterfaces() {
        AgentCard agentCard = createMockAgentCard(
                "http://default:8080",
                "http",
                List.of() // Empty additional interfaces
        );

        NacosAgentCardWrapper wrapper = new NacosAgentCardWrapper(agentCard);

        // Should fallback to default URL
        assertEquals("http://default:8080", wrapper.url());
    }

    @Test
    void testWeightRemoval_WithOtherParameters() {
        AgentCard agentCard = createMockAgentCard(
                "http://default:8080",
                "http",
                Arrays.asList(
                        new AgentInterface("http", "http://host1:8080?token=abc&weight=5&version=1")));

        NacosAgentCardWrapper wrapper = new NacosAgentCardWrapper(agentCard);

        String url = wrapper.url();

        // Should remove weight but keep other parameters
        assertFalse(url.contains("weight="), "Weight parameter should be removed");
        assertTrue(url.contains("token=abc"), "Other parameters should be preserved");
        assertTrue(url.contains("version=1"), "Other parameters should be preserved");
    }

    @Test
    void testTransportFilter_OnlyMatchingTransport() {
        AgentCard agentCard = createMockAgentCard(
                "http://default:8080",
                "sse", // Preferred transport is SSE
                Arrays.asList(
                        new AgentInterface("http", "http://http-host:8080?weight=5"),
                        new AgentInterface("sse", "http://sse-host:8080?weight=3")));

        NacosAgentCardWrapper wrapper = new NacosAgentCardWrapper(agentCard);

        // Should only select SSE interface
        for (int i = 0; i < 10; i++) {
            String url = wrapper.url();
            assertTrue(url.contains("sse-host"), "Should only select SSE transport");
        }
    }

    @Test
    void testDynamicUpdate_WhenAgentCardChanges() {
        AgentCard initialCard = createMockAgentCard(
                "http://default:8080",
                "http",
                Arrays.asList(
                        new AgentInterface("http", "http://host1:8080")));

        NacosAgentCardWrapper wrapper = new NacosAgentCardWrapper(initialCard);
        assertEquals("http://host1:8080", wrapper.url());

        // Update agent card
        AgentCard updatedCard = createMockAgentCard(
                "http://default:8080",
                "http",
                Arrays.asList(
                        new AgentInterface("http", "http://host2:8080")));
        wrapper.setAgentCard(updatedCard);

        // Should now use the new interface
        assertEquals("http://host2:8080", wrapper.url());
    }

    private AgentCard createMockAgentCard(String defaultUrl, String preferredTransport,
            List<AgentInterface> additionalInterfaces) {
        AgentCard agentCard = mock(AgentCard.class);
        when(agentCard.url()).thenReturn(defaultUrl);
        when(agentCard.preferredTransport()).thenReturn(preferredTransport);
        when(agentCard.additionalInterfaces()).thenReturn(additionalInterfaces);

        AgentCapabilities capabilities = mock(AgentCapabilities.class);
        when(capabilities.streaming()).thenReturn(false);
        when(agentCard.capabilities()).thenReturn(capabilities);

        return agentCard;
    }
}
