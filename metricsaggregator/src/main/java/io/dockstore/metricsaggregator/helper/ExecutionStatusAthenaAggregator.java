package io.dockstore.metricsaggregator.helper;

import static java.util.stream.Collectors.groupingBy;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;

import io.dockstore.common.metrics.ExecutionStatus;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.MetricsByStatus;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jooq.Field;
import org.jooq.SelectField;

public class ExecutionStatusAthenaAggregator extends RunExecutionAthenaAggregator<ExecutionStatusMetric> {
    // Aggregators used to calculate metrics by execution status
    private final ExecutionTimeAthenaAggregator executionTimeAggregator = new ExecutionTimeAthenaAggregator();
    private final CpuAthenaAggregator cpuAggregator = new CpuAthenaAggregator();
    private final MemoryAthenaAggregator memoryAggregator = new MemoryAthenaAggregator();
    private final CostAthenaAggregator costAggregator = new CostAthenaAggregator();

    public ExecutionStatusAthenaAggregator() {
        super();
        Field<?> executionStatusField = field(getMetricColumnName());
        Set<SelectField<?>> selectFields = new HashSet<>();
        selectFields.add(coalesce(executionStatusField, inline(ExecutionStatus.ALL.name())).as(getMetricColumnName()));
        selectFields.add(count(executionStatusField).as(getCountColumnName()));
        selectFields.addAll(cpuAggregator.getSelectFields());
        selectFields.addAll(memoryAggregator.getSelectFields());
        selectFields.addAll(costAggregator.getSelectFields());
        this.addSelectFields(selectFields);
        this.addGroupFields(Set.of(executionStatusField)); // Group by status
    }

    @Override
    public String getMetricColumnName() {
        return "executionstatus";
    }

    @Override
    public Optional<ExecutionStatusMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
        Optional<String> executionStatus = queryResultRow.getColumnValue(getMetricColumnName());
        Optional<Integer> executionStatusCount = getCountColumnValue(queryResultRow);
        if (executionStatus.isEmpty() || executionStatusCount.isEmpty()) {
            return Optional.empty();
        }

        MetricsByStatus metricsByStatus = new MetricsByStatus().executionStatusCount(executionStatusCount.get());
        cpuAggregator.createMetricFromQueryResultRow(queryResultRow).ifPresent(metricsByStatus::setCpu);
        memoryAggregator.createMetricFromQueryResultRow(queryResultRow).ifPresent(metricsByStatus::setMemory);
        costAggregator.createMetricFromQueryResultRow(queryResultRow).ifPresent(metricsByStatus::setCost);
        executionTimeAggregator.createMetricFromQueryResultRow(queryResultRow).ifPresent(metricsByStatus::setExecutionTime);
        ExecutionStatusMetric executionStatusMetric = new ExecutionStatusMetric();
        executionStatusMetric.getCount().put(executionStatus.get(), metricsByStatus);
        return Optional.of(executionStatusMetric);
    }

    @Override
    public Map<String, ExecutionStatusMetric> createMetricByPlatform(List<QueryResultRow> queryResultRows) {
        Map<String, ExecutionStatusMetric> metricsByPlatform = new HashMap<>();
        // Group query results by platform
        Map<String, List<QueryResultRow>> queryResultRowsByPlatform = queryResultRows.stream()
                .filter(row -> getPlatformFromQueryResultRow(row).isPresent())
                .collect(groupingBy(row -> getPlatformFromQueryResultRow(row).get()));

        // For each platform, create a metric
        queryResultRowsByPlatform.forEach((platform, queryResultRowsForPlatform) -> {
            Map<String, MetricsByStatus> executionStatusToMetricsByStatus = new HashMap<>();

            queryResultRowsForPlatform.forEach(queryResultRow -> {
                // For each row, create an execution status metric for the single status in the row
                Optional<String> executionStatus = queryResultRow.getColumnValue(getMetricColumnName());
                Optional<ExecutionStatusMetric> executionStatusMetric = createMetricFromQueryResultRow(queryResultRow);
                if (executionStatus.isPresent() && executionStatusMetric.isPresent()) {
                    // Get the MetricsByStatus for the row's execution status from the metric
                    executionStatusToMetricsByStatus.put(executionStatus.get(), executionStatusMetric.get().getCount().get(executionStatus.get()));
                }
            });
            metricsByPlatform.put(platform, new ExecutionStatusMetric().count(executionStatusToMetricsByStatus));
        });

        return metricsByPlatform;
    }
}
