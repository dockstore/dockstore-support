package io.dockstore.metricsaggregator.helper;

import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metric;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.TaskExecutions;
import java.util.List;
import java.util.Objects;

public abstract class RunExecutionAggregator<M extends Metric, E> extends ExecutionAggregator<RunExecution, M, E> {

    @Override
    public List<TaskExecutions> getTaskExecutionsWithMetric(List<TaskExecutions> taskExecutionsList) {
        return taskExecutionsList.stream().filter(taskExecutions -> taskExecutions.getTaskExecutions().stream().map(
                this::getMetricFromExecution).allMatch(Objects::nonNull)).toList();
    }

    @Override
    public List<RunExecution> getExecutionsFromExecutionRequestBody(ExecutionsRequestBody executionsRequestBody) {
        return executionsRequestBody.getRunExecutions();
    }
}
