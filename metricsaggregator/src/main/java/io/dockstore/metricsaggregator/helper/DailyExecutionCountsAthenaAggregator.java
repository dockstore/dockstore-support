package io.dockstore.metricsaggregator.helper;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.date;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.case_;
import static org.jooq.impl.DSL.condition;
import static org.jooq.impl.DSL.toDate;
import static org.jooq.impl.DSL.when;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.TimeSeriesMetric;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jooq.Condition;
import org.jooq.SelectField;

/**
 * TODO
 */
public class DailyExecutionCountsAthenaAggregator extends RunExecutionAthenaAggregator<TimeSeriesMetric> {

    private int binCount;
    private Date now;

    public DailyExecutionCountsAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName, int binCount, Date now) {
        super(metricsAggregatorAthenaClient, tableName);
        this.binCount = binCount;
        this.now = now;
    }

    @Override
    public Set<SelectField<?>> getSelectFields() {
        return IntStream.range(0, binCount).boxed().map(this::getSelectField).collect(Collectors.toSet());
    }

    private SelectField<?> getSelectField(int binOffset) {
        Date lowDate = new Date(); // TODO
	Date highDate = new Date(); // TODO
        String dateFormat = "TODO";
        Condition lowCondition = toDate(DATE_EXECUTED_FIELD, dateFormat).greaterOrEqual(date(lowDate));
        Condition highCondition = toDate(DATE_EXECUTED_FIELD, dateFormat).lessThan(date(highDate));
        Condition withinBin = and(lowCondition, highCondition);
        return count(case_().when(withinBin, 1)).as(getAggregateColumnName(binOffset));
    }

    @Override
    String getMetricColumnName() {
        return DATE_EXECUTED_FIELD.getName();
    }

    private String getAggregateColumnName(int binOffset) {
        return "count_" + binOffset + "_" + getMetricColumnName();
    }

    @Override
    Optional<TimeSeriesMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
        /*
        Optional<String> countColumnValue = queryResultRow.getColumnValue(getAggregateColumnName());
        if (countColumnValue.isPresent()) {
            TimeSeriesMetric metric = new TimeSeriesMetric();
            metric.setValues(List.of(1., 2.));
            return Optional.of(metric);
        } else {
            return Optional.empty();
        }
        */
        return Optional.empty();
    }
}
