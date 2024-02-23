package io.dockstore.metricsaggregator.helper;

import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metric;
import io.dockstore.openapi.client.model.TaskExecutions;
import io.dockstore.openapi.client.model.ValidationExecution;
import java.util.List;

public abstract class ValidationExecutionAggregator<M extends Metric, E> extends ExecutionAggregator<ValidationExecution, M, E> {

    @Override
    public List<TaskExecutions> getTaskExecutionsWithMetric(List<TaskExecutions> taskExecutionsList) {
        return List.of(); // Validation executions don't have task executions
    }

    @Override
    public List<ValidationExecution> getExecutionsFromExecutionRequestBody(ExecutionsRequestBody executionsRequestBody) {
        return executionsRequestBody.getValidationExecutions();
    }
}
