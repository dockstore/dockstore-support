/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.metricsaggregator;

import java.util.List;

/**
 * Record that contains statistical information obtained from a list of Doubles.
 * @param min
 * @param max
 * @param average
 * @param numberOfDataPoints
 */
public record Statistics(double min, double max, double average, int numberOfDataPoints) {
    /**
     * Constructor that calculates statistical information from the provided list of data points.
     * @param dataPoints List of Doubles
     */
    public Statistics(List<Double> dataPoints) {
        this(getMinimum(dataPoints), getMaximum(dataPoints), getAverage(dataPoints), dataPoints.size());
    }

    /**
     * Get the lowest value from the list of data points.
     * @param dataPoints
     * @return
     */
    public static double getMinimum(List<Double> dataPoints) {
        return dataPoints.stream().mapToDouble(d -> d).min().getAsDouble();
    }

    /**
     * Get the highest value from the list of data points.
     * @param dataPoints
     * @return
     */
    public static double getMaximum(List<Double> dataPoints) {
        return dataPoints.stream().mapToDouble(d -> d).max().getAsDouble();
    }

    /**
     * Calculate the average from the list of data points.
     * @param dataPoints
     * @return
     */
    public static double getAverage(List<Double> dataPoints) {
        return dataPoints.stream().mapToDouble(d -> d).average().getAsDouble();
    }
}
