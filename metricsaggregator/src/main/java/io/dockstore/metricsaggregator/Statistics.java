package io.dockstore.metricsaggregator;

import io.dockstore.openapi.client.model.StatisticMetric;
import java.util.List;

/**
 * A class that contains statistical information for a data type.
 * @param <T>
 */
public abstract class Statistics<T> {
    private T minimum;
    private T maximum;
    private T average;
    private int numberOfDataPoints;

    protected Statistics() {
    }

    protected Statistics(T minimum, T maximum, T average, int numberOfDataPoints) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.average = average;
        this.numberOfDataPoints = numberOfDataPoints;
    }

    protected Statistics(List<T> dataPoints) {
        this.minimum = calculateMinimum(dataPoints);
        this.maximum = calculateMaximum(dataPoints);
        this.average = calculateAverage(dataPoints);
        this.numberOfDataPoints = dataPoints.size();
    }

    public abstract T calculateMinimum(List<T> dataPoints);
    public abstract T calculateMaximum(List<T> dataPoints);
    public abstract T calculateAverage(List<T> dataPoints);
    public abstract T calculateWeightedAverage(List<? extends Statistics<T>> statistics);
    public abstract Double getAsDouble(T data);

    public T getMinimum() {
        return minimum;
    }

    public void setMinimum(T min) {
        this.minimum = min;
    }

    public void setMinimum(List<? extends Statistics<T>> statistics) {
        List<T> dataPoints = statistics.stream()
                .map(Statistics::getMinimum)
                .toList();
        this.minimum = calculateMinimum(dataPoints);
    }

    public T getMaximum() {
        return maximum;
    }

    public void setMaximum(T max) {
        this.maximum = max;
    }

    public void setMaximum(List<? extends Statistics<T>> statistics) {
        List<T> dataPoints = statistics.stream()
                .map(Statistics::getMaximum)
                .toList();
        this.maximum = calculateMaximum(dataPoints);
    }

    public T getAverage() {
        return average;
    }

    public void setAverage(T average) {
        this.average = average;
    }

    /**
     * Sets the average by calculating the weighted average from a list of statistics
     * @param statistics
     */
    public void setAverage(List<? extends Statistics<T>> statistics) {
        this.average = calculateWeightedAverage(statistics);
    }

    public int getNumberOfDataPoints() {
        return numberOfDataPoints;
    }

    public void setNumberOfDataPoints(int numberOfDataPoints) {
        this.numberOfDataPoints = numberOfDataPoints;
    }

    public void setNumberOfDataPoints(List<? extends Statistics<T>> statistics) {
        this.numberOfDataPoints = statistics.stream()
                .map(Statistics::getNumberOfDataPoints)
                .mapToInt(Integer::intValue)
                .sum();
    }

    public int getTotalNumberOfDataPoints(List<? extends Statistics<T>> statistics) {
        return statistics.stream()
                .map(Statistics::getNumberOfDataPoints)
                .mapToInt(Integer::intValue)
                .sum();
    }

    /**
     * Sets the minimum, maximum, average, and numberOfDataPointsForAverage for a StatisticMetric
     * @param statisticMetric
     * @return
     * @param <M>
     */
    public <M extends StatisticMetric> M getStatisticMetric(M statisticMetric) {
        statisticMetric.setMinimum(getAsDouble(minimum));
        statisticMetric.setMaximum(getAsDouble(maximum));
        statisticMetric.setAverage(getAsDouble(average));
        statisticMetric.setNumberOfDataPointsForAverage(getNumberOfDataPoints());
        return statisticMetric;
    }
}
