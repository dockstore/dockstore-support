package io.dockstore.metricsaggregator.helper;

import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An interface defining the methods needed to aggregate workflow executions into aggregated metrics from ExecutionsRequestBody S3 objects to submit to Dockstore.
 * @param <T> The type of execution, example: RunExecution or ValidationExecution, that contains the metric to aggregate
 * @param <M> The aggregated metric from the Metrics class, a class containing multiple types of aggregated metrics
 * @param <E> The execution metric to aggregate from the Execution
 */
public interface ExecutionsRequestBodyAggregator<T extends Execution, M, E> extends ExecutionAggregator<T, M, E> {

    /**
     * Get the executions containing the metric to aggregate from ExecutionsRequestBody.
     * @param executionsRequestBody
     * @return
     */
    List<T> getExecutionsFromExecutionRequestBody(ExecutionsRequestBody executionsRequestBody);

    /**
     * Aggregate metrics from all submissions in the ExecutionsRequestBody.
     * This method uses the runExecutions, and taskExecutions from ExecutionRequestBody to create an aggregated metric.
     * Metrics are aggregated by:
     * <ol>
     *     <li>Aggregating task executions, provided via ExecutionRequestBody.taskExecutions, into workflow executions.</li>
     *     <li>Aggregating workflow executions,submitted via ExecutionRequestBody.runExecutions and workflow executions that were aggregated from task executions, into an aggregated metric.
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

        // Aggregate workflow executions into one metric
        return getAggregatedMetricFromExecutions(workflowExecutions);
    }
}
