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
import java.util.ArrayList;
import java.util.Collections;
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
    private Date latestDate;

    public DailyExecutionCountsAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName, int binCount, Date latestDate) {
        super(metricsAggregatorAthenaClient, tableName);
        this.binCount = binCount;
        this.latestDate = latestDate;
    }

    @Override
    public Set<SelectField<?>> getSelectFields() {
        return IntStream.range(0, binCount).boxed().map(this::getSelectField).collect(Collectors.toSet());
    }

    private SelectField<?> getSelectField(int binOffset) {
        Date lowDate = getStartDate(binOffset);
	Date highDate = getEndDate(binOffset);
        String dateFormat = "TODO";
        Condition lowCondition = toDate(DATE_EXECUTED_FIELD, dateFormat).greaterOrEqual(date(lowDate));
        Condition highCondition = toDate(DATE_EXECUTED_FIELD, dateFormat).lessThan(date(highDate));
        Condition withinBin = and(lowCondition, highCondition);
        return count(case_().when(withinBin, 1)).as(getAggregateColumnName(binOffset));
    }


    private String getAggregateColumnName(int binOffset) {
        return "count_" + binOffset + "_" + getMetricColumnName();
    }

    @Override
    String getMetricColumnName() {
        return DATE_EXECUTED_FIELD.getName();
    }

    private Date getStartDate(int binOffset) {
        // round lastDate down to beginning of day
        return new Date(); // TODO
    }

    private Date getEndDate(int binOffset) {
        // add one day to start date
        return new Date(); // TODO
    }

    @Override
    Optional<TimeSeriesMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {

        TimeSeriesMetric metric = new TimeSeriesMetric();

        List<Double> values = new ArrayList<>();
        for (int binOffset: IntStream.range(0, binCount).boxed().toList()) {
            Optional<String> count = queryResultRow.getColumnValue(getAggregateColumnName(binOffset));
            if (count.isPresent()) {
                values.add(Double.parseDouble(count.get()));
            } else {
                return Optional.empty();
            }
        }
        Collections.reverse(values);

        metric.setValues(values);
        // TODO set other time series information

        return Optional.of(metric);
    }
}
