package io.dockstore.metricsaggregator.helper;

import static org.jooq.impl.DSL.aggregate;
import static org.jooq.impl.DSL.avg;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.val;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.Metric;
import java.util.Optional;
import java.util.Set;
import org.jooq.SelectField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StatisticsAthenaAggregator<M extends Metric> extends RunExecutionAthenaAggregator<M> {

    public static final double PERCENTILE_95 = 0.95;
    public static final double PERCENTILE_MEDIAN = 0.50;
    public static final double PERCENTILE_05 = 0.05;

    private static final Logger LOG = LoggerFactory.getLogger(StatisticsAthenaAggregator.class);

    protected StatisticsAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName) {
        super(metricsAggregatorAthenaClient, tableName);
        this.addSelectFields(getStatisticSelectFields());
    }

    /**
     * Returns the set of statistical SELECT fields: min, avg, max, median, percentiles, count
     */
    protected Set<SelectField<?>> getStatisticSelectFields() {
        String approxPercentileFunction = "approx_percentile";
        return Set.of(min(field(getMetricColumnName())).as(getMinColumnName()),
                avg(field(getMetricColumnName(), Double.class)).as(getAvgColumnName()),
                max(field(getMetricColumnName())).as(getMaxColumnName()),
                count(field(getMetricColumnName())).as(getCountColumnName()),
                // note these are custom since jooq isn't quite there, workaround from https://github.com/jOOQ/jOOQ/issues/18706 and also see https://trino.io/docs/current/functions/aggregate.html#approximate-aggregate-functions
                aggregate(approxPercentileFunction, Double.class, field(getMetricColumnName()), val(PERCENTILE_05)).as(getPercentile05thColumnName()),
                aggregate(approxPercentileFunction, Double.class, field(getMetricColumnName()), val(PERCENTILE_MEDIAN)).as(getMedianColumnName()),
                aggregate(approxPercentileFunction, Double.class, field(getMetricColumnName()), val(PERCENTILE_95)).as(getPercentile95thColumnName())
        );
    }

    protected String getMinColumnName() {
        return "min_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    protected String getAvgColumnName() {
        return "avg_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    protected String getMaxColumnName() {
        return "max_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    protected String getPercentile05thColumnName() {
        return "percentile05th_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    protected String getPercentile95thColumnName() {
        return "percentile95th_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    protected String getMedianColumnName() {
        return "median_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    protected Optional<Double> getMinColumnValue(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(getMinColumnName()).map(Double::valueOf);
    }

    protected Optional<Double> getAvgColumnValue(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(getAvgColumnName()).map(Double::valueOf);
    }

    protected Optional<Double> getMaxColumnValue(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(getMaxColumnName()).map(Double::valueOf);
    }

    protected Optional<Double> getMedianColumnValue(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(getMedianColumnName()).map(Double::valueOf);
    }

    protected Optional<Double> getPercentile05thColumnValue(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(getPercentile05thColumnName()).map(Double::valueOf);
    }

    protected Optional<Double> getPercentile95thColumnValue(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(getPercentile95thColumnName()).map(Double::valueOf);
    }

    abstract M createMetricFromStatistics(double min, double avg, double max, double median, double percentile05th, double percentile95th, int numberOfDataPoints);

    @Override
    Optional<M> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
        Optional<Double> min = getMinColumnValue(queryResultRow);
        Optional<Double> avg = getAvgColumnValue(queryResultRow);
        Optional<Double> max = getMaxColumnValue(queryResultRow);
        Optional<Double> median = getMedianColumnValue(queryResultRow);
        Optional<Double> percentile05th = getPercentile05thColumnValue(queryResultRow);
        Optional<Double> percentile95th = getPercentile95thColumnValue(queryResultRow);

        LOG.debug(" ");
        LOG.debug("min: %s".formatted(min.orElse(Double.NaN)));
        LOG.debug("05th: %s".formatted(percentile05th.orElse(Double.NaN)));
        LOG.debug("avg: %s".formatted(avg.orElse(Double.NaN)));
        LOG.debug("median: %s".formatted(median.orElse(Double.NaN)));
        LOG.debug("95th: %s".formatted(percentile95th.orElse(Double.NaN)));
        LOG.debug("max: %s".formatted(max.orElse(Double.NaN)));

        Optional<Integer> numberOfDataPoints = getCountColumnValue(queryResultRow);
        if (min.isPresent() && avg.isPresent() && max.isPresent() && median.isPresent() && percentile05th.isPresent() && percentile95th.isPresent() && numberOfDataPoints.isPresent()) {
            return Optional.of(createMetricFromStatistics(min.get(), avg.get(), max.get(), median.get(), percentile05th.get(), percentile95th.get(), numberOfDataPoints.get()));
        }
        return Optional.empty();
    }
}
