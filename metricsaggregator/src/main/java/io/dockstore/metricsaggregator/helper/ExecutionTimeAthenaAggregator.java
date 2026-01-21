package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;

/**
 * Aggregate execution time metrics statistics using AWS Athena.
 */
public class ExecutionTimeAthenaAggregator extends StatisticsAthenaAggregator<ExecutionTimeMetric> {

    public ExecutionTimeAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName) {
        super(metricsAggregatorAthenaClient, tableName);
    }

    @Override
    String getMetricColumnName() {
        return EXECUTION_TIME_SECONDS_FIELD.getName();
    }

    @Override
    ExecutionTimeMetric createMetricFromStatistics(double min, double avg, double max, double median, double percentile05th, double percentile95th, int numberOfDataPoints) {
        return new ExecutionTimeMetric()
                .minimum(min)
                .average(avg)
                .maximum(max)
                .median(median)
                .percentile05th(percentile05th)
                .percentile95th(percentile95th)
                .numberOfDataPointsForAverage(numberOfDataPoints);
    }
}
