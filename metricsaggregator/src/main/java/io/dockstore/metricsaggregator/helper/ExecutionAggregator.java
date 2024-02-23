package io.dockstore.metricsaggregator.helper;

import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.TaskExecutions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An interface defining the methods needed to aggregate workflow executions into aggregated metrics.
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

    public abstract List<TaskExecutions> getTaskExecutionsWithMetric(List<TaskExecutions> taskExecutionsList);

    /**
     * Aggregates TaskExecutions that belong to a single workflow run into a workflow-level RunExecution. Does NOT check that the resulting workflow run is valid.
     * @param taskExecutionsForOneWorkflowRun
     * @return
     */
    public abstract Optional<T> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun);

    /**
     * Aggregates workflow executions into an aggregated metric.
     * @param executions
     * @return
     */
    public abstract Optional<M> getAggregatedMetricFromExecutions(List<T> executions);

    /**
     * Aggregates a list of aggregated metrics into one aggregated metric.
     * @param aggregatedMetrics
     * @return
     */
    public abstract Optional<M> getAggregatedMetricsFromAggregatedMetrics(List<M> aggregatedMetrics);

    public List<E> getValidMetricsFromExecutions(List<T> executions) {
        return executions.stream()
                .map(this::getMetricFromExecution)
                .filter(executionMetric -> executionMetric != null && validateExecutionMetric(executionMetric))
                .toList();
    }

    public int calculateNumberOfSkippedExecutions(List<T> executions) {
        List<E> executionsWithNonNullMetric = executions.stream().map(this::getMetricFromExecution).filter(Objects::nonNull).toList();
        List<E> validExecutionMetrics = executionsWithNonNullMetric.stream().filter(this::validateExecutionMetric).toList();
        return executionsWithNonNullMetric.size() - validExecutionMetrics.size();
    }

    public int sumNumberOfSkippedExecutions(List<M> metrics) {
        return metrics.stream().map(Metric::getNumberOfSkippedExecutions).reduce(0, Integer::sum);
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
    public Optional<M> getAggregatedMetricFromAllSubmissions(ExecutionsRequestBody allSubmissions) {
        final List<T> workflowExecutions = new ArrayList<>(getExecutionsFromExecutionRequestBody(allSubmissions));

        // Get aggregated metrics that were submitted to Dockstore
        List<M> aggregatedMetrics = allSubmissions.getAggregatedExecutions().stream()
                .map(this::getMetricFromMetrics)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        // If task executions are present, calculate the workflow RunExecution containing the overall workflow-level execution time for each list of tasks
        int numberOfTaskExecutionSetsSkipped = 0;
        if (!allSubmissions.getTaskExecutions().isEmpty()) {
            final List<TaskExecutions> taskExecutionsWithMetric = getTaskExecutionsWithMetric(allSubmissions.getTaskExecutions());
            final List<T> calculatedWorkflowExecutionsFromTasks = taskExecutionsWithMetric.stream()
                    .map(this::getWorkflowExecutionFromTaskExecutions)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            // The number of task execution sets skipped will be added to the total count of skipped executions later
            numberOfTaskExecutionSetsSkipped = taskExecutionsWithMetric.size() - calculatedWorkflowExecutionsFromTasks.size();
            workflowExecutions.addAll(calculatedWorkflowExecutionsFromTasks);
        }

        // Aggregate workflow executions into one metric and add it to the list of aggregated metrics
        Optional<M> aggregatedMetricFromWorkflowExecutions = getAggregatedMetricFromExecutions(workflowExecutions);
        if (aggregatedMetricFromWorkflowExecutions.isPresent()) {
            aggregatedMetricFromWorkflowExecutions.get().setNumberOfSkippedExecutions(calculateNumberOfSkippedExecutions(workflowExecutions));
            aggregatedMetrics.add(aggregatedMetricFromWorkflowExecutions.get());
        }

        if (!aggregatedMetrics.isEmpty()) {
            // Calculate the new aggregated metric from the list of aggregated metrics
            Optional<M> aggregatedMetric = getAggregatedMetricsFromAggregatedMetrics(aggregatedMetrics);
            if (aggregatedMetric.isPresent()) {
                // Set the number of skipped executions to the sum from the aggregated metrics plus the number of task execution sets that were skipped
                aggregatedMetric.get().setNumberOfSkippedExecutions(sumNumberOfSkippedExecutions(aggregatedMetrics) + numberOfTaskExecutionSetsSkipped);
                return aggregatedMetric;
            }

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
