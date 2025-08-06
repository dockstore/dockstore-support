package io.dockstore.metricsaggregator.helper;

import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.TimeSeriesMetric;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jooq.SelectField;

/**
 * Aggregate execution time metrics by calculating the min, average, max, and number of data points using AWS Athena.
 */
public class DailyExecutionCountsAthenaAggregator extends RunExecutionAthenaAggregator<TimeSeriesMetric> {

    public DailyExecutionCountsAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName) {
        super(metricsAggregatorAthenaClient, tableName);
    }

    @Override
    public Set<SelectField<?>> getSelectFields() {
        return Set.of(count(field(getMetricColumnName())).as(getCountColumnName()));
    }

    @Override
    String getMetricColumnName() {
        return DATE_EXECUTED_FIELD.getName();
    }

    private String getAggregateColumnName() {
        return "count_" + getMetricColumnName();
    }

    @Override
    Optional<TimeSeriesMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
        Optional<String> countColumnValue = queryResultRow.getColumnValue(getAggregateColumnName());
        if (countColumnValue.isPresent()) {
            TimeSeriesMetric metric = new TimeSeriesMetric();
            metric.setValues(List.of(1., 2.));
            return Optional.of(metric);
        } else {
            return Optional.empty();
        }
    }
}
