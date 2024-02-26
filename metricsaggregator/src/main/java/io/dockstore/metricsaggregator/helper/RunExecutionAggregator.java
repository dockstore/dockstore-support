package io.dockstore.metricsaggregator.helper;

import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metric;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.TaskExecutions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A class defining the methods needed to aggregate workflow RunExecutions into aggregated metrics.
 * @param <M> The aggregated metric from the Metrics class, a class containing multiple types of aggregated metrics
 * @param <E> The execution metric to aggregate from the Execution
 */
public abstract class RunExecutionAggregator<M extends Metric, E> extends ExecutionAggregator<RunExecution, M, E> {

    public List<TaskExecutions> getTaskExecutionsWithMetric(List<TaskExecutions> taskExecutionsList) {
        return taskExecutionsList.stream().filter(taskExecutions -> taskExecutions.getTaskExecutions().stream().map(
                this::getMetricFromExecution).allMatch(Objects::nonNull)).toList();
    }

    /**
     * Aggregates TaskExecutions that belong to a single workflow run into a workflow-level RunExecution. 
     * Does NOT check that the resulting workflow run is valid. The validity check is done when workflow executions are aggregated so that the aggregator can recognize that task metrics were skipped.
     * @param taskExecutionsForOneWorkflowRun
     * @return
     */
    public abstract Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun);

    @Override
    public List<RunExecution> getExecutionsFromExecutionRequestBody(ExecutionsRequestBody executionsRequestBody) {
        return executionsRequestBody.getRunExecutions();
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
    @Override
    public Optional<M> getAggregatedMetricFromAllSubmissions(ExecutionsRequestBody allSubmissions) {
        final List<RunExecution> workflowExecutions = new ArrayList<>(getExecutionsFromExecutionRequestBody(allSubmissions));

        // Get aggregated metrics that were submitted to Dockstore
        List<M> aggregatedMetrics = allSubmissions.getAggregatedExecutions().stream()
                .map(this::getMetricFromMetrics)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        // If task executions are present, calculate the workflow RunExecution containing the overall workflow-level execution time for each list of tasks
        if (!allSubmissions.getTaskExecutions().isEmpty()) {
            final List<TaskExecutions> taskExecutionsWithMetric = getTaskExecutionsWithMetric(allSubmissions.getTaskExecutions());
            final List<RunExecution> calculatedWorkflowExecutionsFromTasks = taskExecutionsWithMetric.stream()
                    .map(this::getWorkflowExecutionFromTaskExecutions)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            workflowExecutions.addAll(calculatedWorkflowExecutionsFromTasks);
        }

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
