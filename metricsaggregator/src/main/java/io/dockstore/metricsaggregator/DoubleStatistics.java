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
 */
public class DoubleStatistics extends Statistics<Double> {
    public DoubleStatistics() {
        super();
    }
    
    /**
     * Constructor that calculates statistical information from the provided list of data points.
     * @param dataPoints List of Doubles
     */
    public DoubleStatistics(List<Double> dataPoints) {
        super(dataPoints);
    }

    public DoubleStatistics(Double minimum, Double maximum, Double average, int numberOfDataPoints) {
        super(minimum, maximum, average, numberOfDataPoints);
    }

    /**
     * Constructor used to create a Statistics object that can be used to calculate weighted averages for non-Statistics objects.
     * A placeholder value is set for the min and maximum fields
     * @param average
     * @param numberOfDataPoints
     */
    public DoubleStatistics(double average, int numberOfDataPoints) {
        super(0d, 0d, average, numberOfDataPoints);
    }

    /**
     * Create a new Statistics object from a list of statistics by aggregating the list of statistics
     * @param statistics
     * @return
     */
    public static DoubleStatistics createFromStatistics(List<DoubleStatistics> statistics) {
        if (statistics.size() == 1) {
            return statistics.get(0);
        }

        DoubleStatistics newStatistics = new DoubleStatistics();
        newStatistics.setAverage(statistics);
        newStatistics.setMinimum(statistics);
        newStatistics.setMaximum(statistics);
        newStatistics.setNumberOfDataPoints(statistics);
        return newStatistics;
    }

    /**
     * Get the lowest value from the list of data points.
     * @param dataPoints
     * @return
     */
    @Override
    public Double calculateMinimum(List<Double> dataPoints) {
        return dataPoints.stream().mapToDouble(d -> d).min().orElse(0);
    }

    /**
     * Get the highest value from the list of data points.
     * @param dataPoints
     * @return
     */
    @Override
    public Double calculateMaximum(List<Double> dataPoints) {
        return dataPoints.stream().mapToDouble(d -> d).max().orElse(0);
    }

    /**
     * Calculate the average from the list of data points.
     * @param dataPoints
     * @return
     */
    @Override
    public Double calculateAverage(List<Double> dataPoints) {
        return dataPoints.stream().mapToDouble(d -> d).average().orElse(0);
    }

    /**
     * Calculate a weighted average
     */
    @Override
    public Double calculateWeightedAverage(List<? extends Statistics<Double>> statistics) {
        int totalNumberOfDataPoints = getTotalNumberOfDataPoints(statistics);
        return statistics.stream()
                .map(stat -> {
                    double weight = (double)stat.getNumberOfDataPoints() / (double)totalNumberOfDataPoints;
                    return stat.getAverage() * weight;
                })
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    @Override
    public Double getAsDouble(Double data) {
        return data; // already a double
    }
}
