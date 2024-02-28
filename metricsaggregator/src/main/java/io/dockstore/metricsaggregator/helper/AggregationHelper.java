package io.dockstore.metricsaggregator.helper;

import io.dockstore.common.metrics.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AggregationHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AggregationHelper.class);

    private AggregationHelper() {
    }

    /**
     * Aggregate metrics from all submissions.
     *
     * @param allSubmissions
     * @return Metrics object containing aggregated metrics
     */
    public static Optional<Metrics> getAggregatedMetrics(ExecutionsRequestBody allSubmissions) {
        Metrics aggregatedMetrics = new Metrics();
        // Set run metrics
        Optional<ExecutionStatusMetric> aggregatedExecutionStatus = new ExecutionStatusAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions);
        boolean containsRunMetrics = aggregatedExecutionStatus.isPresent();
        aggregatedExecutionStatus.ifPresent(aggregatedMetrics::setExecutionStatusCount);

        // Set validation metrics
        Optional<ValidationStatusMetric> aggregatedValidationStatus = new ValidationStatusAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions);
        boolean containsValidationMetrics = aggregatedValidationStatus.isPresent();
        aggregatedValidationStatus.ifPresent(aggregatedMetrics::setValidationStatus);

        // Only return aggregated metrics if it contains either run metrics or validation metrics
        if (containsRunMetrics || containsValidationMetrics) {
            return Optional.of(aggregatedMetrics);
        }

        return Optional.empty();
    }

    /**
     * Aggregates metrics into a single metric
     * @param aggregatedMetrics
     * @return
     */
    public static Optional<Metrics> getAggregatedMetrics(List<Metrics> aggregatedMetrics) {
        Metrics overallMetrics = new Metrics();
        // Set run metrics
        Optional<ExecutionStatusMetric> aggregatedExecutionStatus = new ExecutionStatusAggregator().getAggregatedMetricsFromAggregatedMetrics(aggregatedMetrics.stream().map(Metrics::getExecutionStatusCount).toList());
        boolean containsRunMetrics = aggregatedExecutionStatus.isPresent();
        aggregatedExecutionStatus.ifPresent(overallMetrics::setExecutionStatusCount);

        // Set validation metrics
        Optional<ValidationStatusMetric> aggregatedValidationStatus = new ValidationStatusAggregator().getAggregatedMetricsFromAggregatedMetrics(aggregatedMetrics.stream().map(Metrics::getValidationStatus).toList());
        boolean containsValidationMetrics = aggregatedValidationStatus.isPresent();
        aggregatedValidationStatus.ifPresent(overallMetrics::setValidationStatus);

        // Only return aggregated metrics if it contains either run metrics or validation metrics
        if (containsRunMetrics || containsValidationMetrics) {
            return Optional.of(overallMetrics);
        }

        return Optional.empty();
    }
}
