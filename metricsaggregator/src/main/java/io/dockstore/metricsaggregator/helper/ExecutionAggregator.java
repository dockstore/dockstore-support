package io.dockstore.metricsaggregator.helper;

import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.TaskExecutions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An interface defining the methods needed to aggregate workflow executions into aggregated metrics.
 * @param <T> The type of execution, example: RunExecution or ValidationExecution, that contains the metric to aggregate
 * @param <M> The aggregated metric from the Metrics class, a class containing multiple types of aggregated metrics
 * @param <E> The execution metric to aggregate from the Execution
 */
public interface ExecutionAggregator<T extends Execution, M, E> {

    /**
     * Get the metric to aggregate from a single workflow execution.
     * @param execution
     * @return
     */
    E getMetricFromExecution(T execution);

    /**
     * Aggregates TaskExecutions that belong to a single workflow run into a workflow-level RunExecution
     * @param taskExecutionsForOneWorkflowRun
     * @return
     */
    Optional<T> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun);

    /**
     * Aggregates workflow executions into an aggregated metric.
     * @param executions
     * @return
     */
    Optional<M> getAggregatedMetricFromExecutions(List<T> executions);

    /**
     * Aggregates a list of aggregated metrics into one aggregated metric.
     * @param aggregatedMetrics
     * @return
     */
    Optional<M> getAggregatedMetricsFromAggregatedMetrics(List<M> aggregatedMetrics);

    /**
     * Returns a list of executions of type T where the execution metric is not null.
     * @param executions
     * @return
     */
    default List<E> getNonNullMetricsFromExecutions(List<T> executions) {
        return executions.stream()
                .map(this::getMetricFromExecution)
                .filter(Objects::nonNull)
                .toList();
    }
}
