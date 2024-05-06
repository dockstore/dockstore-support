package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.MemoryMetric;
import java.util.Optional;

/**
 * Aggregate memory metrics by calculating the min, average, max, and number of data points using AWS Athena.
 */
public class MemoryAthenaAggregator extends AthenaAggregator<MemoryMetric> {
    public MemoryAthenaAggregator() {
        super();
        this.addSelectFields(getStatisticSelectFields());
    }

    @Override
    public String getMetricColumnName() {
        return "memoryrequirementsgb";
    }

    @Override
    public Optional<MemoryMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
        Optional<Double> min = getMinColumnValue(queryResultRow);
        Optional<Double> avg = getAvgColumnValue(queryResultRow);
        Optional<Double> max = getMaxColumnValue(queryResultRow);
        Optional<Integer> numberOfDataPoints = getCountColumnValue(queryResultRow);
        if (min.isPresent() && avg.isPresent() && max.isPresent() && numberOfDataPoints.isPresent()) {
            return Optional.of(new MemoryMetric()
                    .minimum(min.get())
                    .average(avg.get())
                    .maximum(max.get())
                    .numberOfDataPointsForAverage(numberOfDataPoints.get()));
        }
        return Optional.empty();
    }
}
