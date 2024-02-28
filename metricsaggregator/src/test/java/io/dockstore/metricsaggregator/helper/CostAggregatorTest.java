package io.dockstore.metricsaggregator.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.metrics.Cost;
import io.dockstore.common.metrics.RunExecution;
import io.dockstore.common.metrics.TaskExecutions;
import io.dockstore.openapi.client.model.CostMetric;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CostAggregatorTest {
    private static final CostAggregator COST_AGGREGATOR = new CostAggregator();

    /**
     * Tests that the aggregator calculates the correct workflow RunExecution from a list of task RunExecutions.
     */
    @Test
    void testGetWorkflowExecutionFromTaskExecutions() {
        // Empty TaskExecutions should return Optional.empty()
        Optional<RunExecution> workflowExecution = COST_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(new TaskExecutions());
        assertTrue(workflowExecution.isEmpty());

        // The workflow execution generated from a single task should have the same cost as the one task
        TaskExecutions taskExecutions = new TaskExecutions();
        taskExecutions.setTaskExecutions(List.of(createRunExecution(1.0)));
        workflowExecution = COST_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertEquals(1.0, workflowExecution.get().getCost().getValue());

        // The workflow execution generated from multiple tasks should have the sum of costs from the list of tasks
        taskExecutions.setTaskExecutions(List.of(
                createRunExecution(1.0),
                createRunExecution(2.0),
                createRunExecution(3.0)
        ));
        workflowExecution = COST_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertEquals(6.0, workflowExecution.get().getCost().getValue());
    }

    /**
     * Tests that the aggregator calculates the correct aggregated metric from a list of workflow RunExecutions
     */
    @Test
    void testGetAggregatedMetricFromWorkflowExecutions() {
        // Empty list should return Optional.empty()
        Optional<CostMetric> costMetric = COST_AGGREGATOR.getAggregatedMetricFromExecutions(List.of());
        assertTrue(costMetric.isEmpty());

        // Test the metric calculated from a single workflow execution. The min, max, and average should be the same value as the single execution
        List<RunExecution> workflowExecutions = List.of(createRunExecution(1.0));
        costMetric = COST_AGGREGATOR.getAggregatedMetricFromExecutions(workflowExecutions);
        assertTrue(costMetric.isPresent());
        assertEquals(1.0, costMetric.get().getMinimum());
        assertEquals(1.0, costMetric.get().getMaximum());
        assertEquals(1.0, costMetric.get().getAverage());
        assertEquals(1, costMetric.get().getNumberOfDataPointsForAverage());

        // Test the metric calculated from multiple workflow executions.
        workflowExecutions = List.of(
                createRunExecution(2.0),
                createRunExecution(4.0),
                createRunExecution(6.0)
        );
        costMetric = COST_AGGREGATOR.getAggregatedMetricFromExecutions(workflowExecutions);
        assertTrue(costMetric.isPresent());
        assertEquals(2.0, costMetric.get().getMinimum());
        assertEquals(6.0, costMetric.get().getMaximum());
        assertEquals(4.0, costMetric.get().getAverage());
        assertEquals(3, costMetric.get().getNumberOfDataPointsForAverage());
    }

    private RunExecution createRunExecution(Double costValue) {
        RunExecution runExecution = new RunExecution();
        runExecution.setCost(new Cost(costValue));
        return runExecution;
    }
}
