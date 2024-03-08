package io.dockstore.metricsaggregator.helper;

import io.dockstore.common.metrics.ExecutionsRequestBody;
import io.dockstore.common.metrics.ValidationExecution;
import io.dockstore.openapi.client.model.Metric;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A class defining the methods needed to aggregate workflow ValidationExecutions into aggregated metrics.
 * @param <M> The aggregated metric from the Metrics class, a class containing multiple types of aggregated metrics
 * @param <E> The execution metric to aggregate from the Execution
 */
public abstract class ValidationExecutionAggregator<M extends Metric, E> extends ExecutionAggregator<ValidationExecution, M, E> {

    /**
     * Aggregate metrics from all submissions in the ExecutionsRequestBody.
     * This method uses the validationExecutions from ExecutionRequestBody to create an aggregated metric.
     * Metrics are aggregated by:
     * <ol>
     *     <li>Aggregating workflow executions,submitted via ExecutionRequestBody.validationExecutions into an aggregated metric.
     * </ol>
     * @param allSubmissions
     * @return
     */
    public Optional<M> getAggregatedMetricFromAllSubmissions(ExecutionsRequestBody allSubmissions) {
        final List<ValidationExecution> workflowExecutions = new ArrayList<>(allSubmissions.getValidationExecutions());

        // Aggregate workflow executions into one metric
        return getAggregatedMetricFromExecutions(workflowExecutions);
    }
}
