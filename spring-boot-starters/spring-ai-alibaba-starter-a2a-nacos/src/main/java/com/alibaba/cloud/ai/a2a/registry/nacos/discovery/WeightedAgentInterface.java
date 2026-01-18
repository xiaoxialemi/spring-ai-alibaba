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

/**
 * Weighted wrapper for Agent Interface, used for weighted load balancing.
 * Wraps an agent interface URL with weight information for the smooth weighted
 * round-robin algorithm.
 *
 * @author Claude
 */
public class WeightedAgentInterface {

    /**
     * Default weight value when not configured.
     */
    public static final int DEFAULT_WEIGHT = 1;

    /**
     * The URL of the agent interface.
     */
    private final String url;

    /**
     * The transport protocol (e.g., "http", "sse").
     */
    private final String transport;

    /**
     * The fixed weight configured for this interface.
     * This value does not change during runtime.
     */
    private final int weight;

    /**
     * The current weight used in smooth weighted round-robin algorithm.
     * This value changes dynamically during load balancing.
     */
    private int currentWeight;

    /**
     * Creates a new WeightedAgentInterface with specified weight.
     *
     * @param url       the URL of the agent interface
     * @param transport the transport protocol
     * @param weight    the weight for load balancing (must be positive)
     */
    public WeightedAgentInterface(String url, String transport, int weight) {
        this.url = url;
        this.transport = transport;
        this.weight = Math.max(weight, 1); // Ensure weight is at least 1
        this.currentWeight = 0;
    }

    /**
     * Creates a new WeightedAgentInterface with default weight (1).
     *
     * @param url       the URL of the agent interface
     * @param transport the transport protocol
     */
    public WeightedAgentInterface(String url, String transport) {
        this(url, transport, DEFAULT_WEIGHT);
    }

    public String getUrl() {
        return url;
    }

    public String getTransport() {
        return transport;
    }

    public int getWeight() {
        return weight;
    }

    public int getCurrentWeight() {
        return currentWeight;
    }

    public void setCurrentWeight(int currentWeight) {
        this.currentWeight = currentWeight;
    }

    /**
     * Increases the current weight by the fixed weight value.
     * Used in smooth weighted round-robin algorithm.
     */
    public void increaseCurrentWeight() {
        this.currentWeight += this.weight;
    }

    /**
     * Decreases the current weight by the specified amount.
     * Typically called after this interface is selected.
     *
     * @param amount the amount to decrease (usually total weight)
     */
    public void decreaseCurrentWeight(int amount) {
        this.currentWeight -= amount;
    }

    @Override
    public String toString() {
        return "WeightedAgentInterface{" +
                "url='" + url + '\'' +
                ", transport='" + transport + '\'' +
                ", weight=" + weight +
                ", currentWeight=" + currentWeight +
                '}';
    }
}
