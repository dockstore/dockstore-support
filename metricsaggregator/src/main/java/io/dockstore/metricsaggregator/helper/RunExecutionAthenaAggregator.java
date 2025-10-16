package io.dockstore.metricsaggregator.helper;

import static org.jooq.impl.DSL.aggregate;
import static org.jooq.impl.DSL.avg;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.cube;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.val;

import io.dockstore.common.Partner;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.AthenaTablePartition;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.Metric;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Select;
import org.jooq.SelectField;
import org.jooq.conf.Settings;
import org.jooq.conf.StatementType;
import org.jooq.impl.DSL;

public abstract class RunExecutionAthenaAggregator<M extends Metric> extends AthenaAggregator<M> {

    public static final double PERCENTILE_95 = 0.95;
    public static final double PERCENTILE_MEDIAN = 0.50;
    public static final double PERCENTILE_05 = 0.05;
    protected static final Field<String> EXECUTION_STATUS_FIELD = field("executionstatus", String.class);
    protected static final Field<Integer> EXECUTION_TIME_SECONDS_FIELD = field("executiontimeseconds", Integer.class);
    protected static final Field<Double> MEMORY_REQUIREMENTS_GB_FIELD = field("memoryrequirementsgb", Double.class);
    protected static final Field<Integer> CPU_REQUIREMENTS_FIELD = field("cpurequirements", Integer.class);
    protected static final Field<String> COST_FIELD = field("cost", String.class);
    protected static final Field<String[]> TASK_EXECUTIONS_FIELD = field("taskexecutions", String[].class);

    // Fields for the SELECT clause
    private final Set<SelectField<?>> selectFields = new HashSet<>();
    // Fields for the GROUP BY clause
    private final Set<Field<?>> groupFields = new HashSet<>();

    protected RunExecutionAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName) {
        super(metricsAggregatorAthenaClient, tableName);
        // All queries will be grouped by platform at a minimum
        selectFields.add(coalesce(PLATFORM_FIELD, inline(Partner.ALL.name())).as(PLATFORM_FIELD.getName())); // Coalesce null platform values to "ALL"
        groupFields.add(PLATFORM_FIELD);
    }

    /**
     * Create a metric from a single query result row.
     * @param queryResultRow
     * @return
     */
    abstract Optional<M> createMetricFromQueryResultRow(QueryResultRow queryResultRow);

    /**
     * Get the column name containing the metric.
     * Athena columns are lowercase, but if a non-lowercase column is provided (ex: camelCase), Athena automatically lowercases it.
     * @return
     */
    abstract String getMetricColumnName();

    public Set<SelectField<?>> getSelectFields() {
        return this.selectFields;
    }

    public void addSelectFields(Set<SelectField<?>> newSelectFields) {
        this.selectFields.addAll(newSelectFields);
    }

    public Set<Field<?>> getGroupFields() {
        return this.groupFields;
    }

    public void addGroupFields(Set<Field<?>> newGroupFields) {
        this.groupFields.addAll(newGroupFields);
    }

    /**
     * Create the runexecutions query string using the SELECT and GROUP BY fields.
     * De-duplicates executions with the same execution ID by taking the newest execution according to the S3 file modified time.
     *
     * @return
     */
    @Override
    protected String createQuery(AthenaTablePartition partition) {
        // Sub-query that flattens the runexecutions array for the partition
        List<Field<?>> runExecutionFields = List.of(DATE_EXECUTED_FIELD, EXECUTION_STATUS_FIELD, EXECUTION_TIME_SECONDS_FIELD, MEMORY_REQUIREMENTS_GB_FIELD, CPU_REQUIREMENTS_FIELD, COST_FIELD);
        final Select<Record> dedupedRunExecutions = createUnnestQueryWithModifiedTime(partition, field("runexecutions", String[].class), runExecutionFields);

        // This query creates a workflow execution from each array of tasks. This will be unioned with the actual workflow executions submitted.
        // We turn each array of tasks into a workflow execution and union it with the workflow executions submitted because this is how we currently aggregate tasks.
        List<Field<?>> taskExecutionFields = List.of(
                PLATFORM_FIELD,
                field("array_min(transform(taskexecutions, t -> t.dateexecuted))", String.class).as(DATE_EXECUTED_FIELD),
                // If all tasks are successful, set the workflow execution status as successful. Otherwise, assume failed
                field("case when all_match(transform(taskexecutions, t -> t.executionstatus), t -> t = 'SUCCESSFUL') then 'SUCCESSFUL' else 'FAILED' end", String.class).as(EXECUTION_STATUS_FIELD),
                field("array_max(transform(taskexecutions, t -> t.executiontimeseconds))", Integer.class).as(EXECUTION_TIME_SECONDS_FIELD),
                field("array_max(transform(taskexecutions, t -> t.memoryrequirementsgb))", Double.class).as(MEMORY_REQUIREMENTS_GB_FIELD),
                field("array_max(transform(taskexecutions, t -> t.cpurequirements))", Integer.class).as(CPU_REQUIREMENTS_FIELD),
                field("array_max(transform(taskexecutions, t -> t.cost))", String.class).as(COST_FIELD)
        );
        final Select<Record> dedupedTaskExecutions = createUnnestQueryWithModifiedTime(partition, field("taskexecutions", String[].class), List.of(field("taskexecutions")));
        final Select<Record> runExecutionsFromTasks = select(taskExecutionFields)
                .from(dedupedTaskExecutions);

        return DSL.using(SQLDialect.DEFAULT, new Settings().withRenderFormatted(true).withStatementType(StatementType.STATIC_STATEMENT))
                // Main query that uses the results of the subquery
                .select(this.selectFields)
                .from(dedupedRunExecutions
                        .unionAll(runExecutionsFromTasks)
                )
                .groupBy(cube(this.groupFields.toArray(Field[]::new))) // CUBE generates sub-totals for all combinations of the GROUP BY columns.
                .getSQL();
    }

    /**
     * Given a list of query result rows, creates a metric for each row and maps it to a platform
     * @param queryResultRows
     * @return
     */
    @Override
    protected Map<String, M> createMetricByPlatform(List<QueryResultRow> queryResultRows) {
        Map<String, M> metricByPlatform = new HashMap<>();
        queryResultRows.forEach(queryResultRow -> {
            Optional<String> platform = getPlatformFromQueryResultRow(queryResultRow);
            if (platform.isPresent()) {
                Optional<M> metric = createMetricFromQueryResultRow(queryResultRow);
                metric.ifPresent(m -> metricByPlatform.put(platform.get(), m));
            }
        });
        return metricByPlatform;
    }

    /**
     * Returns a set of statistical SELECT fields: min, avg, max, count
     *
     * @return
     */
    protected Set<SelectField<?>> getStatisticSelectFields() {
        String approxPercentileFunction = "approx_percentile";
        return Set.of(min(field(getMetricColumnName())).as(getMinColumnName()),
                avg(field(getMetricColumnName(), Double.class)).as(getAvgColumnName()),
                max(field(getMetricColumnName())).as(getMaxColumnName()),
                count(field(getMetricColumnName())).as(getCountColumnName()),
                // note these are custom since jooq isn't quite there, workaround from https://github.com/jOOQ/jOOQ/issues/18706 and also see https://trino.io/docs/current/functions/aggregate.html#approximate-aggregate-functions
                aggregate(approxPercentileFunction, Double.class, field(getMetricColumnName()), val(PERCENTILE_05)).as(getMedianColumnName()),
                aggregate(approxPercentileFunction, Double.class, field(getMetricColumnName()), val(PERCENTILE_MEDIAN)).as(get5thPercentileColumnName()),
                aggregate(approxPercentileFunction, Double.class, field(getMetricColumnName()), val(PERCENTILE_95)).as(get95thPercentileColumnName())
        );
    }

    protected String substitutePeriodsForUnderscores(String columnName) {
        return columnName.replace(".", "_");
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

    protected String get5thPercentileColumnName() {
        return "percentile05th_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    protected String get95thPercentileColumnName() {
        return "percentile95th_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    protected String getMedianColumnName() {
        return "median_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    protected String getCountColumnName() {
        return "count_" + substitutePeriodsForUnderscores(getMetricColumnName());
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

    protected Optional<Double> get05thPercentileColumnValue(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(get5thPercentileColumnName()).map(Double::valueOf);
    }
    protected Optional<Double> get95thPercentileColumnValue(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(get95thPercentileColumnName()).map(Double::valueOf);
    }


    protected Optional<Integer> getCountColumnValue(QueryResultRow queryResultRow) {
        Optional<Integer> countColumnValue = queryResultRow.getColumnValue(getCountColumnName()).map(Integer::valueOf);
        if (countColumnValue.isPresent() && countColumnValue.get() == 0) { // There were 0 non-null column values
            return Optional.empty();
        }
        return countColumnValue;
    }
}
