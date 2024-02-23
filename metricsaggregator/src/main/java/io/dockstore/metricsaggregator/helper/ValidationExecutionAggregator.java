package io.dockstore.metricsaggregator.helper;

import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metric;
import io.dockstore.openapi.client.model.ValidationExecution;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A class defining the methods needed to aggregate workflow ValidationExecutions into aggregated metrics.
 * @param <M> The aggregated metric from the Metrics class, a class containing multiple types of aggregated metrics
 * @param <E> The execution metric to aggregate from the Execution
 */
public abstract class ValidationExecutionAggregator<M extends Metric, E> extends ExecutionAggregator<ValidationExecution, M, E> {

    @Override
    public List<ValidationExecution> getExecutionsFromExecutionRequestBody(ExecutionsRequestBody executionsRequestBody) {
        return executionsRequestBody.getValidationExecutions();
    }

    /**
     * Aggregate metrics from all submissions in the ExecutionsRequestBody.
     * This method uses the validationExecutions, and aggregatedExecutions from ExecutionRequestBody to create an aggregated metric.
     * Metrics are aggregated by:
     * <ol>
     *     <li>Aggregating workflow executions,submitted via ExecutionRequestBody.validationExecutions into an aggregated metric.
     *     <li>Aggregating the list of aggregated metrics, submitted via ExecutionRequestBody.aggregatedExecutions and the aggregated metric that was aggregated from workflow executions, into one aggregated metric.</li>
     * </ol>
     * @param allSubmissions
     * @return
     */
    public Optional<M> getAggregatedMetricFromAllSubmissions(ExecutionsRequestBody allSubmissions) {
        final List<ValidationExecution> workflowExecutions = new ArrayList<>(getExecutionsFromExecutionRequestBody(allSubmissions));

        // Get aggregated metrics that were submitted to Dockstore
        List<M> aggregatedMetrics = allSubmissions.getAggregatedExecutions().stream()
                .map(this::getMetricFromMetrics)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        // Aggregate workflow executions into one metric and add it to the list of aggregated metrics
        Optional<M> aggregatedMetricFromWorkflowExecutions = getAggregatedMetricFromExecutions(workflowExecutions);
        aggregatedMetricFromWorkflowExecutions.ifPresent(aggregatedMetrics::add);

        if (!aggregatedMetrics.isEmpty()) {
            // Calculate the new aggregated metric from the list of aggregated metrics
            return getAggregatedMetricsFromAggregatedMetrics(aggregatedMetrics);
        }
        return Optional.empty();
    }
}
