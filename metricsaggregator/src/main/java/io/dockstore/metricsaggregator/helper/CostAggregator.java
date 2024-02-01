package io.dockstore.metricsaggregator.helper;

import static io.dockstore.common.metrics.FormatCheckHelper.isValidCurrencyCode;
import static io.dockstore.metricsaggregator.MoneyStatistics.CURRENCY;

import io.dockstore.metricsaggregator.MoneyStatistics;
import io.dockstore.openapi.client.model.Cost;
import io.dockstore.openapi.client.model.CostMetric;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.TaskExecutions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.javamoney.moneta.Money;

public class CostAggregator implements ExecutionAggregator<RunExecution, CostMetric, Cost> {
    @Override
    public CostMetric getMetricFromMetrics(Metrics metrics) {
        return null;
    }

    @Override
    public Cost getMetricFromExecution(RunExecution execution) {
        return execution.getCost();
    }

    @Override
    public List<RunExecution> getExecutionsFromExecutionRequestBody(ExecutionsRequestBody executionsRequestBody) {
        return executionsRequestBody.getRunExecutions();
    }

    @Override
    public Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun) {
        final List<RunExecution> taskExecutions = taskExecutionsForOneWorkflowRun.getTaskExecutions();
        if (taskExecutions != null && taskExecutions.stream().map(RunExecution::getCost).allMatch(Objects::nonNull)) {
            // Get the overall cost by summing up the cost of each task
            List<Cost> taskCosts = taskExecutions.stream()
                    .map(RunExecution::getCost)
                    .toList();
            boolean containsMalformedCurrencies = taskCosts.stream().anyMatch(cost -> !isValidCurrencyCode(cost.getCurrency()));
            // This shouldn't happen until we allow users to submit any currency they want
            if (!containsMalformedCurrencies && !taskCosts.isEmpty()) {
                Money totalCost = taskCosts.stream()
                        .map(cost -> Money.of(cost.getValue(), cost.getCurrency()))
                        .reduce(Money.of(0, CURRENCY), Money::add);
                return Optional.of(new RunExecution().cost(new Cost().value(totalCost.getNumber().doubleValue())));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<CostMetric> getAggregatedMetricFromExecutions(List<RunExecution> executions) {
        List<Cost> submittedCosts = getNonNullMetricsFromExecutions(executions);

        boolean containsMalformedCurrencies = submittedCosts.stream().anyMatch(cost -> !isValidCurrencyCode(cost.getCurrency()));
        // This shouldn't happen until we allow users to submit any currency they want
        if (containsMalformedCurrencies) {
            return Optional.empty(); // Don't aggregate if there's malformed data
        }

        if (!submittedCosts.isEmpty()) {
            List<Money> costs = submittedCosts.stream()
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
    public Optional<CostMetric> getAggregatedMetricsFromAggregatedMetrics(List<CostMetric> aggregatedMetrics) {
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
