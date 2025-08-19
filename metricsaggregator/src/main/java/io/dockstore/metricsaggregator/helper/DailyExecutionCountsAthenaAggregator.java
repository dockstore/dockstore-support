package io.dockstore.metricsaggregator.helper;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.case_;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.instant;

// import static org.jooq.impl.DSL.val;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.TimeSeriesMetric;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SelectField;

/**
 * TODO
 */
public class DailyExecutionCountsAthenaAggregator extends RunExecutionAthenaAggregator<TimeSeriesMetric> {

    private static final int ZONE_HOUR_OFFSET = -4;
    private static final ZoneId ZONE_ID = ZoneOffset.ofHours(ZONE_HOUR_OFFSET); // Always aggregate with an Eastern DST time offset, so that bin boundaries align with Easterm DST midnight and don't shift depending on where the aggregator is run and/or as Daylight Savings Time comes and goes.
    private final int binCount;
    private final Instant now;

    public DailyExecutionCountsAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName, int binCount, Instant now) {
        super(metricsAggregatorAthenaClient, tableName);
        this.binCount = binCount;
        this.now = now;
    }

    @Override
    public Set<SelectField<?>> getSelectFields() {
        return getBinOffsets().stream().map(this::getSelectField2).collect(Collectors.toSet());
    }

    private SelectField<?> getSelectField(int binOffset) {
        Field<Instant> start = instant(getBinStart(binOffset).toInstant());
        Field<Instant> end = instant(getBinEnd(binOffset).toInstant());
        Field<Instant> whenExecuted = function("from_iso8601_timestamp", Instant.class, DATE_EXECUTED_FIELD);
        Condition withinBin = and(whenExecuted.greaterOrEqual(start), whenExecuted.lessThan(end));
        String aggregateColumnName = getAggregateColumnName(binOffset);
        return count(case_().when(withinBin, 1)).as(aggregateColumnName);
    }

    private SelectField<?> getSelectField2(int binOffset) {
        return count(DATE_EXECUTED_FIELD);
    }

    private OffsetDateTime getBinStart(int binOffset) {
        return OffsetDateTime.ofInstant(now, ZONE_ID).truncatedTo(ChronoUnit.DAYS).minusDays(binOffset);
    }

    private OffsetDateTime getBinEnd(int binOffset) {
        return getBinStart(binOffset).plusDays(1);
    }

    private String getAggregateColumnName(int binOffset) {
        return "count_" + binOffset + "_" + getMetricColumnName();
    }

    @Override
    String getMetricColumnName() {
        return DATE_EXECUTED_FIELD.getName();
    }

    private List<Integer> getBinOffsets() {
        return IntStream.range(0, binCount).boxed().toList();
    }

    @Override
    Optional<TimeSeriesMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {

        TimeSeriesMetric metric = new TimeSeriesMetric();

        List<Double> values = new ArrayList<>();
        for (int binOffset: getBinOffsets()) {
            Optional<String> count = queryResultRow.getColumnValue(getAggregateColumnName(binOffset));
            if (count.isPresent()) {
                values.add(Double.parseDouble(count.get()));
            } else {
                return Optional.empty();
            }
        }
        Collections.reverse(values);

        metric.setValues(values);
        metric.setInterval(TimeSeriesMetric.IntervalEnum.DAY);
        metric.setBegins(Date.from(getBinStart(binCount - 1).toInstant()));

        return Optional.of(metric);
    }
}
