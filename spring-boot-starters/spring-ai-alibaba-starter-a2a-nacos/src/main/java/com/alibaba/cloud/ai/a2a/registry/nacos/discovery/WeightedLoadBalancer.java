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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Smooth Weighted Round-Robin Load Balancer.
 * <p>
 * This load balancer implements the smooth weighted round-robin algorithm,
 * which distributes traffic according to configured weights while ensuring
 * smooth distribution without traffic spikes.
 * </p>
 * 
 * <p>
 * Algorithm:
 * </p>
 * <ol>
 * <li>For each selection, increase all instances' currentWeight by their
 * weight</li>
 * <li>Select the instance with the highest currentWeight</li>
 * <li>Decrease the selected instance's currentWeight by totalWeight</li>
 * </ol>
 * 
 * <p>
 * Example with weights A=5, B=3, C=2 (total=10):
 * </p>
 * 
 * <pre>
 * Round 1: A selected (weights become: A=-5, B=3, C=2)
 * Round 2: B selected (weights become: A=0, B=-4, C=4)
 * Round 3: A selected (weights become: A=-5, B=-1, C=6)
 * ...
 * </pre>
 *
 * @author Claude
 */
public class WeightedLoadBalancer {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeightedLoadBalancer.class);

    /**
     * List of weighted agent interfaces.
     */
    private final List<WeightedAgentInterface> interfaces;

    /**
     * Total weight of all interfaces.
     */
    private int totalWeight;

    /**
     * Creates a new WeightedLoadBalancer with the given interfaces.
     *
     * @param interfaces list of weighted agent interfaces
     */
    public WeightedLoadBalancer(List<WeightedAgentInterface> interfaces) {
        this.interfaces = new ArrayList<>(interfaces);
        this.totalWeight = calculateTotalWeight();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("WeightedLoadBalancer initialized with {} interfaces, totalWeight={}",
                    this.interfaces.size(), this.totalWeight);
        }
    }

    /**
     * Creates an empty WeightedLoadBalancer.
     */
    public WeightedLoadBalancer() {
        this.interfaces = new ArrayList<>();
        this.totalWeight = 0;
    }

    /**
     * Calculate the total weight of all interfaces.
     */
    private int calculateTotalWeight() {
        return interfaces.stream()
                .mapToInt(WeightedAgentInterface::getWeight)
                .sum();
    }

    /**
     * Select the next URL using smooth weighted round-robin algorithm.
     * <p>
     * This method is thread-safe.
     * </p>
     *
     * @return the selected URL, or null if no interfaces available
     */
    public synchronized String selectUrl() {
        if (interfaces.isEmpty()) {
            return null;
        }

        if (interfaces.size() == 1) {
            return interfaces.get(0).getUrl();
        }

        // Step 1: Increase all currentWeight by their weight
        for (WeightedAgentInterface iface : interfaces) {
            iface.increaseCurrentWeight();
        }

        // Step 2: Find the interface with the highest currentWeight
        WeightedAgentInterface selected = null;
        int maxCurrentWeight = Integer.MIN_VALUE;

        for (WeightedAgentInterface iface : interfaces) {
            if (iface.getCurrentWeight() > maxCurrentWeight) {
                maxCurrentWeight = iface.getCurrentWeight();
                selected = iface;
            }
        }

        if (selected == null) {
            // Fallback: should not happen, but return first if it does
            selected = interfaces.get(0);
        }

        // Step 3: Decrease the selected interface's currentWeight by totalWeight
        selected.decreaseCurrentWeight(totalWeight);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Selected URL: {} (weight={}, currentWeight={})",
                    selected.getUrl(), selected.getWeight(), selected.getCurrentWeight());
        }

        return selected.getUrl();
    }

    /**
     * Update the interfaces list with new interfaces.
     * Resets all currentWeight values.
     *
     * @param newInterfaces the new list of interfaces
     */
    public synchronized void updateInterfaces(List<WeightedAgentInterface> newInterfaces) {
        this.interfaces.clear();
        this.interfaces.addAll(newInterfaces);
        this.totalWeight = calculateTotalWeight();

        // Reset current weights
        for (WeightedAgentInterface iface : interfaces) {
            iface.setCurrentWeight(0);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("WeightedLoadBalancer updated with {} interfaces, totalWeight={}",
                    this.interfaces.size(), this.totalWeight);
        }
    }

    /**
     * Check if the load balancer has any interfaces.
     *
     * @return true if no interfaces are available
     */
    public boolean isEmpty() {
        return interfaces.isEmpty();
    }

    /**
     * Get the number of interfaces.
     *
     * @return the number of interfaces
     */
    public int size() {
        return interfaces.size();
    }

    /**
     * Get the total weight.
     *
     * @return the total weight
     */
    public int getTotalWeight() {
        return totalWeight;
    }
}
