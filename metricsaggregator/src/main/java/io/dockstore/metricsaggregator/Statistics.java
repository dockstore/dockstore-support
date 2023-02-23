package io.dockstore.metricsaggregator;

import java.util.List;

public record Statistics(double min, double max, double average, int numberOfDataPoints) {
    public Statistics(List<Double> dataPoints) {
        this(getMinimum(dataPoints), getMaximum(dataPoints), getAverage(dataPoints), dataPoints.size());
    }

    public static double getMinimum(List<Double> dataPoints) {
        return dataPoints.stream().mapToDouble(d -> d).min().getAsDouble();
    }

    public static double getMaximum(List<Double> dataPoints) {
        return dataPoints.stream().mapToDouble(d -> d).max().getAsDouble();
    }

    public static double getAverage(List<Double> dataPoints) {
        return dataPoints.stream().mapToDouble(d -> d).average().getAsDouble();
    }
}
