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

public interface Aggregator<M, E> {

    M getMetricFromMetrics(Metrics metrics);
    E getMetricFromRunExecution(RunExecution runExecution);
    Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun);
    Optional<M> getAggregatedMetricFromWorkflowExecutions(List<RunExecution> workflowExecutions);
    Optional<M> getAggregatedMetricsFromAggregatedMetrics(List<M> aggregatedMetrics);

    default List<E> getNonNullMetricsFromRunExecutions(List<RunExecution> runExecutions) {
        return runExecutions.stream()
                .map(execution -> getMetricFromRunExecution(execution))
                .filter(Objects::nonNull)
                .toList();
    }

    default Optional<M> getAggregatedMetricFromAllSubmissions(ExecutionsRequestBody allSubmissions) {
        final List<RunExecution> workflowExecutions = new ArrayList<>(allSubmissions.getRunExecutions());

        // If task executions are present, calculate the workflow RunExecution containing the overall workflow-level execution time
        if (!allSubmissions.getTaskExecutions().isEmpty()) {
            final List<RunExecution> calculatedWorkflowExecutionsFromTasks = allSubmissions.getTaskExecutions().stream()
                    .map(taskExecutions -> getWorkflowExecutionFromTaskExecutions(taskExecutions))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            workflowExecutions.addAll(calculatedWorkflowExecutionsFromTasks);
        }

        // Get aggregated Execution Time metrics that were submitted to Dockstore
        List<M> aggregatedMetrics = allSubmissions.getAggregatedExecutions().stream()
                .map(metrics -> getMetricFromMetrics(metrics))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        // Aggregate workflow executions into one metric
        Optional<M> aggregatedMetricFromWorkflowExecutions = getAggregatedMetricFromWorkflowExecutions(workflowExecutions);
        aggregatedMetricFromWorkflowExecutions.ifPresent(aggregatedMetrics::add);

        if (!aggregatedMetrics.isEmpty()) {
            // Calculate the new aggregated metrics from the list of aggregated metrics
            return getAggregatedMetricsFromAggregatedMetrics(aggregatedMetrics);
        }
        return Optional.empty();
    }
}
