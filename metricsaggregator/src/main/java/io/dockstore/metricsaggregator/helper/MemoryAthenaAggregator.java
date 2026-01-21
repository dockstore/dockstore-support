package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.openapi.client.model.MemoryMetric;

/**
 * Aggregate memory metric statistics using AWS Athena.
 */
public class MemoryAthenaAggregator extends StatisticsAthenaAggregator<MemoryMetric> {
    public MemoryAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName) {
        super(metricsAggregatorAthenaClient, tableName);
    }

    @Override
    String getMetricColumnName() {
        return MEMORY_REQUIREMENTS_GB_FIELD.getName();
    }

    @Override
    MemoryMetric createMetricFromStatistics(double min, double avg, double max, double median, double percentile05th, double percentile95th, int numberOfDataPoints) {
        return new MemoryMetric()
                .minimum(min)
                .average(avg)
                .maximum(max)
                .median(median)
                .percentile05th(percentile05th)
                .percentile95th(percentile95th)
                .numberOfDataPointsForAverage(numberOfDataPoints);
    }
}
