package io.dockstore.metricsaggregator.helper;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.case_;
import static org.jooq.impl.DSL.cast;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.timestamp;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.TimeSeriesMetric;
import java.sql.Timestamp;
import java.time.Duration;
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
 * Aggregate a time series of execution counts.  This aggregator creates a SelectField for each time series "bin" (consisting of a SQL `count()` statement on a corresponding date range).  These SelectFields are submitted with the Athena query, and the resulting counts are assembled into a TimeSeriesMetric object.
 */
public class DailyExecutionCountsAthenaAggregator extends RunExecutionAthenaAggregator<TimeSeriesMetric> {

    private static final int ZONE_HOUR_OFFSET = -4;  // Always aggregate with an Eastern DST time offset, so that all bin boundaries align with Eastern DST midnight and don't shift depending upon where the aggregator is run and/or whether Daylight Savings Time is currently in effect (or not).
    private static final ZoneId ZONE_ID = ZoneOffset.ofHours(ZONE_HOUR_OFFSET);
    private final int binCount;
    private final Instant now;

    /**
     * Create an aggregator that computes a time series with the specified number of bins (representing a consecutive series of days), with the youngest (last) bin overlapping the specified date.
     * @param binCount the number of bins (days) to aggregate
     * @param now the instant which is included in the youngest bin
     */
    public DailyExecutionCountsAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName, int binCount, Instant now) {
        super(metricsAggregatorAthenaClient, tableName);
        this.binCount = binCount;
        this.now = now;
    }

    @Override
    public Set<SelectField<?>> getSelectFields() {
        return getBinAges().stream().map(this::getSelectField).collect(Collectors.toSet());
    }

    private SelectField<?> getSelectField(int binAge) {
        Field<Timestamp> start = timestamp(toDate(getBinStart(binAge)));
        Field<Timestamp> end = timestamp(toDate(getBinEnd(binAge)));
        Field<Timestamp> whenExecuted = cast(function("from_iso8601_timestamp", Instant.class, DATE_EXECUTED_FIELD), Timestamp.class);
        Condition withinBin = and(whenExecuted.greaterOrEqual(start), whenExecuted.lessThan(end));
        String aggregateColumnName = getAggregateColumnName(binAge);
        return count(case_().when(withinBin, 1)).as(aggregateColumnName);
    }

    private OffsetDateTime getBinStart(int binAge) {
        return OffsetDateTime.ofInstant(now, ZONE_ID).truncatedTo(ChronoUnit.DAYS).minusDays(binAge);
    }

    private OffsetDateTime getBinEnd(int binAge) {
        return getBinStart(binAge).plusDays(1);
    }

    private OffsetDateTime getBinMidpoint(int binAge) {
        OffsetDateTime start = getBinStart(binAge);
        OffsetDateTime end = getBinEnd(binAge);
        return start.plus(Duration.between(start, end).dividedBy(2));
    }

    private List<Integer> getBinAges() {
        return IntStream.range(0, binCount).boxed().toList();
    }

    private Date toDate(OffsetDateTime offsetDateTime) {
        return Date.from(offsetDateTime.toInstant());
    }

    private String getAggregateColumnName(int binAge) {
        return "count_" + binAge + "_" + getMetricColumnName();
    }

    @Override
    String getMetricColumnName() {
        return DATE_EXECUTED_FIELD.getName();
    }

    @Override
    Optional<TimeSeriesMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
        // Create the list of "values", consisting of the execution count for each time series "bin", ordered oldest to newest.
        List<Double> values = new ArrayList<>();
        for (int binAge: getBinAges()) {
            Optional<String> count = queryResultRow.getColumnValue(getAggregateColumnName(binAge));
            if (count.isPresent()) {
                values.add(count.map(Double::valueOf).get());
            } else {
                return Optional.empty();
            }
        }
        Collections.reverse(values);

        // Construct, populate, and return the TimeSeriesMetric object.
        TimeSeriesMetric metric = new TimeSeriesMetric();
        metric.setValues(values);
        metric.setInterval(TimeSeriesMetric.IntervalEnum.DAY);
        metric.setBegins(toDate(getBinMidpoint(binCount - 1)));
        return Optional.of(metric);
    }
}
