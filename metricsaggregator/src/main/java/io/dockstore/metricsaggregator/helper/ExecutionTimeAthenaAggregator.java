package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate execution time metrics by calculating the min, average, max, and number of data points using AWS Athena.
 */
public class ExecutionTimeAthenaAggregator extends RunExecutionAthenaAggregator<ExecutionTimeMetric> {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionTimeAthenaAggregator.class);


    public ExecutionTimeAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName) {
        super(metricsAggregatorAthenaClient, tableName);
        this.addSelectFields(getStatisticSelectFields());
    }

    @Override
    String getMetricColumnName() {
        return EXECUTION_TIME_SECONDS_FIELD.getName();
    }

    @Override
    Optional<ExecutionTimeMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
        Optional<Double> min = getMinColumnValue(queryResultRow);
        Optional<Double> avg = getAvgColumnValue(queryResultRow);
        Optional<Double> max = getMaxColumnValue(queryResultRow);
        Optional<Double> median = getMedianColumnValue(queryResultRow);
        Optional<Double> percentile05th = getPercentile05thColumnValue(queryResultRow);
        Optional<Double> percentile95th = getPercentile95thColumnValue(queryResultRow);

        LOG.debug(" ");
        LOG.debug("min: %s".formatted(min.orElse(Double.NaN)));
        LOG.debug("05th: %s".formatted(percentile05th.orElse(Double.NaN)));
        LOG.debug("avg: %s".formatted(avg.orElse(Double.NaN)));
        LOG.debug("median: %s".formatted(median.orElse(Double.NaN)));
        LOG.debug("95th: %s".formatted(percentile95th.orElse(Double.NaN)));
        LOG.debug("max: %s".formatted(max.orElse(Double.NaN)));

        Optional<Integer> numberOfDataPoints = getCountColumnValue(queryResultRow);
        if (min.isPresent() && avg.isPresent() && max.isPresent() && median.isPresent() && percentile05th.isPresent() && percentile95th.isPresent() && numberOfDataPoints.isPresent()) {
            return Optional.of(new ExecutionTimeMetric()
                .minimum(min.get())
                .average(avg.get())
                .maximum(max.get())
                .median(median.get())
                .percentile05th(percentile05th.get())
                .percentile95th(percentile95th.get())
                .numberOfDataPointsForAverage(numberOfDataPoints.get()));
        }
        return Optional.empty();
    }
}
