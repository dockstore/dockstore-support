package io.dockstore.metricsaggregator.helper;

import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An interface defining the methods needed to aggregate workflow executions into aggregated metrics from ExecutionsRequestBody S3 objects to submit to Dockstore.
 * @param <T> The type of execution, example: RunExecution or ValidationExecution, that contains the metric to aggregate
 * @param <M> The aggregated metric from the Metrics class, a class containing multiple types of aggregated metrics
 * @param <E> The execution metric to aggregate from the Execution
 */
public interface ExecutionsRequestBodyAggregator<T extends Execution, M, E> extends ExecutionAggregator<T, M, E> {

    /**
     * Get the aggregated metric associated with the metric type from the aggregated Metrics class, which contains multiple types of aggregated metrics.
     * @param metrics
     * @return
     */
    M getMetricFromMetrics(Metrics metrics);

    /**
     * Get the executions containing the metric to aggregate from ExecutionsRequestBody.
     * @param executionsRequestBody
     * @return
     */
    List<T> getExecutionsFromExecutionRequestBody(ExecutionsRequestBody executionsRequestBody);

    /**
     * Aggregate metrics from all submissions in the ExecutionsRequestBody.
     * This method uses the runExecutions, taskExecutions, and aggregatedExecutions from ExecutionRequestBody to create an aggregated metric.
     * Metrics are aggregated by:
     * <ol>
     *     <li>Aggregating task executions, provided via ExecutionRequestBody.taskExecutions, into workflow executions.</li>
     *     <li>Aggregating workflow executions,submitted via ExecutionRequestBody.runExecutions and workflow executions that were aggregated from task executions, into an aggregated metric.
     *     <li>Aggregating the list of aggregated metrics, submitted via ExecutionRequestBody.aggregatedExecutions and the aggregated metric that was aggregated from workflow executions, into one aggregated metric.</li>
     * </ol>
     * @param allSubmissions
     * @return
     */
    default Optional<M> getAggregatedMetricFromAllSubmissions(ExecutionsRequestBody allSubmissions) {
        final List<T> workflowExecutions = new ArrayList<>(getExecutionsFromExecutionRequestBody(allSubmissions));

        // If task executions are present, calculate the workflow RunExecution containing the overall workflow-level execution time for each list of tasks
        if (!allSubmissions.getTaskExecutions().isEmpty()) {
            final List<T> calculatedWorkflowExecutionsFromTasks = allSubmissions.getTaskExecutions().stream()
                    .map(this::getWorkflowExecutionFromTaskExecutions)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            workflowExecutions.addAll(calculatedWorkflowExecutionsFromTasks);
        }

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

    /**
     * Given a list of Metrics, a class containing multiple types of aggregated metrics, get the metrics associated with the metric type and
     * aggregate them into a metric of this type.
     * @param metricsList
     * @return
     */
    default Optional<M> getAggregatedMetricFromMetricsList(List<Metrics> metricsList) {
        List<M> specificMetrics = metricsList.stream()
                .map(this::getMetricFromMetrics)
                .filter(Objects::nonNull)
                .toList();
        return getAggregatedMetricsFromAggregatedMetrics(specificMetrics);
    }
}
