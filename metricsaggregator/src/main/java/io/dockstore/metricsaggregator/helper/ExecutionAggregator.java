package io.dockstore.metricsaggregator.helper;

import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metric;
import io.dockstore.openapi.client.model.Metrics;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A class defining the methods needed to aggregate workflow executions into aggregated metrics.
 * @param <T> The type of execution, example: RunExecution or ValidationExecution, that contains the metric to aggregate
 * @param <M> The aggregated metric from the Metrics class, a class containing multiple types of aggregated metrics
 * @param <E> The execution metric to aggregate from the Execution
 */
public abstract class ExecutionAggregator<T extends Execution, M extends Metric, E> {

    /**
     * Get the metric to aggregate from a single workflow execution.
     * @param execution
     * @return
     */
    public abstract E getMetricFromExecution(T execution);

    /**
     * Get the aggregated metric associated with the metric type from the aggregated Metrics class, which contains multiple types of aggregated metrics.
     * @param metrics
     * @return
     */
    public abstract M getMetricFromMetrics(Metrics metrics);

    /**
     * Get the executions containing the metric to aggregate from ExecutionsRequestBody.
     * @param executionsRequestBody
     * @return
     */
    public abstract List<T> getExecutionsFromExecutionRequestBody(ExecutionsRequestBody executionsRequestBody);

    /**
     * Returns a boolean indicating if the execution metric is valid.
     * @param executionMetric
     * @return
     */
    public abstract boolean validateExecutionMetric(E executionMetric);

    /**
     * Aggregates workflow executions into an aggregated metric.
     * @param executionMetrics
     * @return
     */
    protected abstract Optional<M> calculateAggregatedMetricFromExecutionMetrics(List<E> executionMetrics);

    /**
     * Aggregates a list of aggregated metrics into one aggregated metric.
     * @param aggregatedMetrics
     * @return
     */
    protected abstract Optional<M> calculateAggregatedMetricFromAggregatedMetrics(List<M> aggregatedMetrics);

    /**
     * Aggregate metrics from all submissions in the ExecutionsRequestBody.
     * @param allSubmissions
     * @return
     */
    public abstract Optional<M> getAggregatedMetricFromAllSubmissions(ExecutionsRequestBody allSubmissions);

    /**
     * Aggregates workflow executions into an aggregated metric and calculates the number of skipped executions.
     * @param executions
     * @return
     */

    public final Optional<M> getAggregatedMetricFromExecutions(List<T> executions) {
        final List<E> executionsWithNonNullMetric = executions.stream().map(this::getMetricFromExecution).filter(Objects::nonNull).toList();
        final List<E> validExecutionMetrics = executionsWithNonNullMetric.stream().filter(this::validateExecutionMetric).toList();
        if (!validExecutionMetrics.isEmpty()) {
            Optional<M> calculatedMetric = calculateAggregatedMetricFromExecutionMetrics(validExecutionMetrics);
            final int numberOfSkippedExecutions = executionsWithNonNullMetric.size() - validExecutionMetrics.size();
            calculatedMetric.ifPresent(metric -> metric.setNumberOfSkippedExecutions(numberOfSkippedExecutions));
            return calculatedMetric;
        }
        return Optional.empty();
    }

    /**
     * Aggregates a list of aggregated metrics into one aggregated metric  nd calculates the number of skipped executions.
     * @param aggregatedMetrics
     * @return
     */
    public final Optional<M> getAggregatedMetricsFromAggregatedMetrics(List<M> aggregatedMetrics) {
        if (!aggregatedMetrics.isEmpty()) {
            Optional<M> calculatedMetric = calculateAggregatedMetricFromAggregatedMetrics(aggregatedMetrics);
            // Sum number of skipped executions from the aggregated metrics
            final int numberOfSkippedExecutions =  aggregatedMetrics.stream().map(Metric::getNumberOfSkippedExecutions).reduce(0, Integer::sum);
            calculatedMetric.ifPresent(metric -> metric.setNumberOfSkippedExecutions(numberOfSkippedExecutions));
            return calculatedMetric;
        }
        return Optional.empty();
    }

    /**
     * Given a list of Metrics, a class containing multiple types of aggregated metrics, get the metrics associated with the metric type and
     * aggregate them into a metric of this type.
     * @param metricsList
     * @return
     */
    public Optional<M> getAggregatedMetricFromMetricsList(List<Metrics> metricsList) {
        List<M> specificMetrics = metricsList.stream()
                .map(this::getMetricFromMetrics)
                .filter(Objects::nonNull)
                .toList();
        return getAggregatedMetricsFromAggregatedMetrics(specificMetrics);
    }
}
