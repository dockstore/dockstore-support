package io.dockstore.metricsaggregator;

import java.util.List;
import org.javamoney.moneta.Money;

/**
 * Calculates money statistics in USD using the Java Money library to preserve accuracy.
 */
public class MoneyStatistics extends Statistics<Money> {
    public static final String CURRENCY = "USD";

    private MoneyStatistics() {
        super();
    }

    public MoneyStatistics(List<Money> dataPoints) {
        super(dataPoints);
    }

    public MoneyStatistics(Money minimum, Money maximum, Money average, int numberOfDataPoints) {
        super(minimum, maximum, average, numberOfDataPoints);
    }

    /**
     * Create a new Statistics object from a list of statistics by aggregating the list of statistics
     * @param statistics
     * @return
     */
    public static MoneyStatistics createFromStatistics(List<MoneyStatistics> statistics) {
        if (statistics.size() == 1) {
            return statistics.get(0);
        }

        MoneyStatistics newStatistics = new MoneyStatistics();
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
    public Money calculateMinimum(List<Money> dataPoints) {
        return dataPoints.stream()
                .min(Money::compareTo)
                .orElse(Money.of(0, CURRENCY));
    }

    /**
     * Get the highest value from the list of data points.
     * @param dataPoints
     * @return
     */
    @Override
    public Money calculateMaximum(List<Money> dataPoints) {
        return dataPoints.stream()
                .max(Money::compareTo)
                .orElse(Money.of(0, CURRENCY));
    }

    /**
     * Calculate the average from the list of data points.
     * @param dataPoints
     * @return
     */
    @Override
    public Money calculateAverage(List<Money> dataPoints) {
        Money sum = dataPoints.stream().reduce(Money.of(0, CURRENCY), Money::add);
        return sum.divide(dataPoints.size());
    }

    /**
     * Calculate a weighted average
     */
    @Override
    public Money calculateWeightedAverage(List<? extends Statistics<Money>> statistics) {
        int totalNumberOfDataPoints = getTotalNumberOfDataPoints(statistics);
        return statistics.stream()
                .map(stat -> {
                    double weight = (double)stat.getNumberOfDataPoints() / (double)totalNumberOfDataPoints;
                    return stat.getAverage().multiply(weight);
                })
                .reduce(Money.of(0, CURRENCY), Money::add);
    }
}
