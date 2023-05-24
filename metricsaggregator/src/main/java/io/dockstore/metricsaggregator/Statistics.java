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
import java.util.stream.Stream;

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
     * Constructor used to create a Statistics object that can be used to calculate weighted averages for non-Statistics objects.
     * A placeholder value is set for the min and maximum fields
     * @param average
     * @param numberOfDataPoints
     */
    public Statistics(double average, int numberOfDataPoints) {
        this(0, 0, average, numberOfDataPoints);
    }

    /**
     * Create a new Statistics object from a list of statistics by aggregating the list of statistics
     * @param statistics
     * @return
     */
    public static Statistics createFromStatistics(List<Statistics> statistics) {
        if (statistics.size() == 1) {
            return statistics.get(0);
        }
        List<Double> dataPoints =  statistics.stream()
                .flatMap(stat -> Stream.of(stat.min(), stat.max()))
                .toList();
        double min = getMinimum(dataPoints);
        double max = getMaximum(dataPoints);
        double average = getWeightedAverage(statistics);
        int numberOfDataPoints = statistics.stream().map(Statistics::numberOfDataPoints).mapToInt(Integer::intValue).sum();
        return new Statistics(min, max, average, numberOfDataPoints);
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

    /**
     * Calculate a weighted average
     */
    public static double getWeightedAverage(List<Statistics> statistics) {
        int totalNumberOfDataPoints = statistics.stream().map(Statistics::numberOfDataPoints).mapToInt(Integer::intValue).sum();
        return statistics.stream()
                .map(stat -> {
                    double weight = (double)stat.numberOfDataPoints() / (double)totalNumberOfDataPoints;
                    return stat.average() * weight;
                })
                .mapToDouble(Double::doubleValue)
                .sum();
    }
}
