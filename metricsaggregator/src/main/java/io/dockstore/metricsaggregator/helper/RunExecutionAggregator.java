package io.dockstore.metricsaggregator.helper;

import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.TaskExecutions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An interface defining the methods needed to aggregate RunExecution's
 * @param <M> The aggregated metric from Metrics
 * @param <E> The execution metric from RunExecution
 */
public interface RunExecutionAggregator<M, E> {

    M getMetricFromMetrics(Metrics metrics);
    E getMetricFromRunExecution(RunExecution runExecution);

    /**
     * Aggregates TaskExecutions that belong to a single workflow run into a workflow-level RunExecution
     * @param taskExecutionsForOneWorkflowRun
     * @return
     */
    Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun);

    /**
     * Aggregates workflow executions into an aggregated metric.
     * @param workflowExecutions
     * @return
     */
    Optional<M> getAggregatedMetricFromWorkflowExecutions(List<RunExecution> workflowExecutions);

    /**
     * Aggregates a list of aggregated metrics into one aggregated metric.
     * @param aggregatedMetrics
     * @return
     */
    Optional<M> getAggregatedMetricsFromAggregatedMetrics(List<M> aggregatedMetrics);

    /**
     * Returns a list of RunExecutions where the execution metric is not null.
     * @param runExecutions
     * @return
     */
    default List<E> getNonNullMetricsFromRunExecutions(List<RunExecution> runExecutions) {
        return runExecutions.stream()
                .map(this::getMetricFromRunExecution)
                .filter(Objects::nonNull)
                .toList();
    }

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
        final List<RunExecution> workflowExecutions = new ArrayList<>(allSubmissions.getRunExecutions());

        // If task executions are present, calculate the workflow RunExecution containing the overall workflow-level execution time for each list of tasks
        if (!allSubmissions.getTaskExecutions().isEmpty()) {
            final List<RunExecution> calculatedWorkflowExecutionsFromTasks = allSubmissions.getTaskExecutions().stream()
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
        Optional<M> aggregatedMetricFromWorkflowExecutions = getAggregatedMetricFromWorkflowExecutions(workflowExecutions);
        aggregatedMetricFromWorkflowExecutions.ifPresent(aggregatedMetrics::add);

        if (!aggregatedMetrics.isEmpty()) {
            // Calculate the new aggregated metric from the list of aggregated metrics
            return getAggregatedMetricsFromAggregatedMetrics(aggregatedMetrics);
        }
        return Optional.empty();
    }
}
