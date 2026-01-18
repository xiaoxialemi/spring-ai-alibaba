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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WeightedLoadBalancer.
 * Verifies the smooth weighted round-robin algorithm implementation.
 */
class WeightedLoadBalancerTest {

    @Test
    void testSingleInstance_AlwaysSelected() {
        List<WeightedAgentInterface> interfaces = Arrays.asList(
                new WeightedAgentInterface("http://host1:8080", "http", 1));
        WeightedLoadBalancer lb = new WeightedLoadBalancer(interfaces);

        // Single instance should always be selected
        for (int i = 0; i < 10; i++) {
            assertEquals("http://host1:8080", lb.selectUrl());
        }
    }

    @Test
    void testTwoInstances_EqualWeight_AlternateSelection() {
        List<WeightedAgentInterface> interfaces = Arrays.asList(
                new WeightedAgentInterface("http://host1:8080", "http", 1),
                new WeightedAgentInterface("http://host2:8080", "http", 1));
        WeightedLoadBalancer lb = new WeightedLoadBalancer(interfaces);

        // With equal weights, should alternate
        Map<String, Integer> counts = new HashMap<>();
        counts.put("http://host1:8080", 0);
        counts.put("http://host2:8080", 0);

        for (int i = 0; i < 100; i++) {
            String url = lb.selectUrl();
            counts.put(url, counts.get(url) + 1);
        }

        // Each should be selected roughly 50 times
        assertEquals(50, counts.get("http://host1:8080"));
        assertEquals(50, counts.get("http://host2:8080"));
    }

    @Test
    void testWeightedSelection_HigherWeightGetsMoreTraffic() {
        // A has weight 5, B has weight 3, C has weight 2 (total 10)
        List<WeightedAgentInterface> interfaces = Arrays.asList(
                new WeightedAgentInterface("http://hostA:8080", "http", 5),
                new WeightedAgentInterface("http://hostB:8080", "http", 3),
                new WeightedAgentInterface("http://hostC:8080", "http", 2));
        WeightedLoadBalancer lb = new WeightedLoadBalancer(interfaces);

        Map<String, Integer> counts = new HashMap<>();
        counts.put("http://hostA:8080", 0);
        counts.put("http://hostB:8080", 0);
        counts.put("http://hostC:8080", 0);

        // Run 100 selections (should be 10 complete rounds)
        for (int i = 0; i < 100; i++) {
            String url = lb.selectUrl();
            counts.put(url, counts.get(url) + 1);
        }

        // Verify distribution matches weights: A=50%, B=30%, C=20%
        assertEquals(50, counts.get("http://hostA:8080"), "Host A should be selected 50 times");
        assertEquals(30, counts.get("http://hostB:8080"), "Host B should be selected 30 times");
        assertEquals(20, counts.get("http://hostC:8080"), "Host C should be selected 20 times");
    }

    @Test
    void testSmoothDistribution_NoTrafficSpike() {
        // Weight 5 and weight 1 (total 6)
        List<WeightedAgentInterface> interfaces = Arrays.asList(
                new WeightedAgentInterface("http://hostA:8080", "http", 5),
                new WeightedAgentInterface("http://hostB:8080", "http", 1));
        WeightedLoadBalancer lb = new WeightedLoadBalancer(interfaces);

        // In smooth weighted round-robin, we should never see 5 consecutive A's
        int consecutiveA = 0;
        int maxConsecutiveA = 0;

        for (int i = 0; i < 60; i++) {
            String url = lb.selectUrl();
            if ("http://hostA:8080".equals(url)) {
                consecutiveA++;
                maxConsecutiveA = Math.max(maxConsecutiveA, consecutiveA);
            } else {
                consecutiveA = 0;
            }
        }

        // With smooth distribution, max consecutive A should be <= 2
        assertTrue(maxConsecutiveA <= 2,
                "Smooth distribution should not have more than 2 consecutive selections of A, but got "
                        + maxConsecutiveA);
    }

    @Test
    void testEmptyLoadBalancer_ReturnsNull() {
        WeightedLoadBalancer lb = new WeightedLoadBalancer();
        assertNull(lb.selectUrl());
        assertTrue(lb.isEmpty());
    }

    @Test
    void testUpdateInterfaces_ResetsWeights() {
        List<WeightedAgentInterface> initialInterfaces = Arrays.asList(
                new WeightedAgentInterface("http://host1:8080", "http", 5),
                new WeightedAgentInterface("http://host2:8080", "http", 1));
        WeightedLoadBalancer lb = new WeightedLoadBalancer(initialInterfaces);

        // Make some selections to change currentWeight
        for (int i = 0; i < 5; i++) {
            lb.selectUrl();
        }

        // Update interfaces
        List<WeightedAgentInterface> newInterfaces = Arrays.asList(
                new WeightedAgentInterface("http://host3:8080", "http", 1));
        lb.updateInterfaces(newInterfaces);

        // Should now select the new interface
        assertEquals("http://host3:8080", lb.selectUrl());
        assertEquals(1, lb.size());
    }

    @Test
    void testGetTotalWeight() {
        List<WeightedAgentInterface> interfaces = Arrays.asList(
                new WeightedAgentInterface("http://host1:8080", "http", 5),
                new WeightedAgentInterface("http://host2:8080", "http", 3),
                new WeightedAgentInterface("http://host3:8080", "http", 2));
        WeightedLoadBalancer lb = new WeightedLoadBalancer(interfaces);

        assertEquals(10, lb.getTotalWeight());
    }
}
