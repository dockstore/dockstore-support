package io.dockstore.metricsaggregator.helper;

import static io.dockstore.common.metrics.FormatCheckHelper.isValidCurrencyCode;
import static io.dockstore.metricsaggregator.MoneyStatistics.CURRENCY;

import io.dockstore.common.metrics.Cost;
import io.dockstore.common.metrics.RunExecution;
import io.dockstore.common.metrics.TaskExecutions;
import io.dockstore.metricsaggregator.MoneyStatistics;
import io.dockstore.openapi.client.model.CostMetric;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.javamoney.moneta.Money;

public class CostAggregator extends RunExecutionAggregator<CostMetric, Cost> {

    @Override
    public Cost getMetricFromExecution(RunExecution execution) {
        return execution.getCost();
    }

    @Override
    public boolean validateExecutionMetric(Cost executionMetric) {
        return executionMetric != null && isValidCurrencyCode(executionMetric.getCurrency()) && executionMetric.getValue() >= 0;
    }

    @Override
    public String getPropertyPathToValidate() {
        return "cost";
    }

    @Override
    public Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun) {
        final List<RunExecution> taskExecutions = taskExecutionsForOneWorkflowRun.getTaskExecutions();
        if (!taskExecutions.isEmpty() && taskExecutions.stream().map(RunExecution::getCost).allMatch(Objects::nonNull)) {
            // Get the overall cost by summing up the cost of each task
            List<Cost> taskCosts = taskExecutions.stream()
                    .map(RunExecution::getCost)
                    .filter(Objects::nonNull)
                    .toList();
            if (!taskCosts.isEmpty()) {
                Money totalCost = taskCosts.stream()
                        .map(cost -> Money.of(cost.getValue(), cost.getCurrency()))
                        .reduce(Money.of(0, CURRENCY), Money::add);
                RunExecution workflowExecution = new RunExecution();
                workflowExecution.setCost(new Cost(totalCost.getNumber().doubleValue()));
                return Optional.of(workflowExecution);
            }
        }
        return Optional.empty();
    }

    @Override
    protected Optional<CostMetric> calculateAggregatedMetricFromExecutionMetrics(List<Cost> executionMetrics) {
        if (!executionMetrics.isEmpty()) {
            List<Money> costs = executionMetrics.stream()
                    .map(cost -> Money.of(cost.getValue(), cost.getCurrency()))
                    .toList();
            MoneyStatistics statistics = new MoneyStatistics(costs);
            return Optional.of(new CostMetric()
                    .minimum(statistics.getMinimum().getNumber().doubleValue())
                    .maximum(statistics.getMaximum().getNumber().doubleValue())
                    .average(statistics.getAverage().getNumber().doubleValue())
                    .numberOfDataPointsForAverage(statistics.getNumberOfDataPoints()));
        }
        return Optional.empty();
    }

    @Override
    protected Optional<CostMetric> calculateAggregatedMetricFromAggregatedMetrics(List<CostMetric> aggregatedMetrics) {
        if (!aggregatedMetrics.isEmpty()) {
            List<MoneyStatistics> statistics = aggregatedMetrics.stream()
                    .map(metric -> new MoneyStatistics(
                            Money.of(metric.getMinimum(), metric.getUnit()),
                            Money.of(metric.getMaximum(), metric.getUnit()),
                            Money.of(metric.getAverage(), metric.getUnit()),
                            metric.getNumberOfDataPointsForAverage()))
                    .toList();
            MoneyStatistics moneyStatistics = MoneyStatistics.createFromStatistics(statistics);
            return Optional.of(new CostMetric()
                    .minimum(moneyStatistics.getMinimum().getNumber().doubleValue())
                    .maximum(moneyStatistics.getMaximum().getNumber().doubleValue())
                    .average(moneyStatistics.getAverage().getNumber().doubleValue())
                    .numberOfDataPointsForAverage(moneyStatistics.getNumberOfDataPoints()));
        }
        return Optional.empty();
    }
}
