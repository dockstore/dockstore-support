package io.dockstore.metricsaggregator.helper;

import static org.jooq.impl.DSL.avg;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.cube;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.unnest;

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
import org.jooq.CommonTableExpression;
import org.jooq.Field;
import org.jooq.Record7;
import org.jooq.SQLDialect;
import org.jooq.Select;
import org.jooq.SelectField;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

public abstract class RunExecutionAthenaAggregator<M extends Metric> extends AthenaAggregator<M> {
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
     *
     * @return
     */
    @Override
    protected String createQuery(AthenaTablePartition partition) {
        // Sub-query that flattens the runexecutions array for the partition
        final Select<?> runExecutionsNestedSelect = select(FILE_MODIFIED_TIME_FIELD,
                rowNumber().over().orderBy(FILE_MODIFIED_TIME_FIELD.desc()).as(FILE_MODIFIED_TIME_ROW_NUM_FIELD),
                PLATFORM_FIELD,
                field("unnested." + DATE_EXECUTED_FIELD, String.class),
                field("unnested." + EXECUTION_STATUS_FIELD, String.class),
                field("unnested." + EXECUTION_TIME_SECONDS_FIELD, Integer.class),
                field("unnested." + MEMORY_REQUIREMENTS_GB_FIELD, Double.class),
                field("unnested." + CPU_REQUIREMENTS_FIELD, Integer.class),
                field("unnested." + COST_FIELD, String.class))
                .from(table(tableName), unnest(field("runexecutions", String[].class)).as("t", "unnested"))
                .where(ENTITY_FIELD.eq(inline(partition.entity()))
                        .and(REGISTRY_FIELD.eq(inline(partition.registry())))
                        .and(ORG_FIELD.eq(inline(partition.org())))
                        .and(NAME_FIELD.eq(inline(partition.name())))
                        .and(VERSION_FIELD.eq(inline(partition.version()))));

        final CommonTableExpression<Record7<String, String, String, Integer, Double, Integer, String>> dedupedExecutionsTable = name("dedupedexecutions")
                .fields(PLATFORM_FIELD.getName(), DATE_EXECUTED_FIELD.getName(), EXECUTION_STATUS_FIELD.getName(), EXECUTION_TIME_SECONDS_FIELD.getName(),
                        MEMORY_REQUIREMENTS_GB_FIELD.getName(), CPU_REQUIREMENTS_FIELD.getName(), COST_FIELD.getName())
                .as(select(PLATFORM_FIELD, DATE_EXECUTED_FIELD, EXECUTION_STATUS_FIELD, EXECUTION_TIME_SECONDS_FIELD,
                        MEMORY_REQUIREMENTS_GB_FIELD, CPU_REQUIREMENTS_FIELD, COST_FIELD)
                        .from(runExecutionsNestedSelect)
                        .where(FILE_MODIFIED_TIME_ROW_NUM_FIELD.eq(inline(1))));

        final Select<?> taskExecutionsListNestedSelect = select(FILE_MODIFIED_TIME_FIELD,
                rowNumber().over().orderBy(FILE_MODIFIED_TIME_FIELD.desc()).as(FILE_MODIFIED_TIME_ROW_NUM_FIELD),
                PLATFORM_FIELD,
                field("unnested." + TASK_EXECUTIONS_FIELD, String[].class))
                .from(table(tableName), unnest(field("taskexecutions", String[].class)).as("t", "unnested"))
                .where(ENTITY_FIELD.eq(inline(partition.entity()))
                                .and(REGISTRY_FIELD.eq(inline(partition.registry())))
                                .and(ORG_FIELD.eq(inline(partition.org())))
                                .and(NAME_FIELD.eq(inline(partition.name())))
                                .and(VERSION_FIELD.eq(inline(partition.version()))));

        // This query created a workflow execution from each array of tasks. This will be unioned with the actual workflow executions submitted
        final CommonTableExpression<Record7<String, String, String, Integer, Double, Integer, String>> dedupedRunExecutionsFromTasksTable = name("runexecutionfromdedupedtasks")
                .fields(PLATFORM_FIELD.getName(), DATE_EXECUTED_FIELD.getName(), EXECUTION_STATUS_FIELD.getName(), EXECUTION_TIME_SECONDS_FIELD.getName(),
                        MEMORY_REQUIREMENTS_GB_FIELD.getName(), CPU_REQUIREMENTS_FIELD.getName(), COST_FIELD.getName())
                .as(select(PLATFORM_FIELD,
                        field("array_min(transform(taskexecutions, t -> t.dateexecuted))", String.class).as(DATE_EXECUTED_FIELD),
                        // If all tasks are successful, set the workflow execution status as successful. Otherwise, assume failed
                        field("case when all_match(transform(taskexecutions, t -> t.executionstatus), t -> t = 'SUCCESSFUL') then 'SUCCESSFUL' else 'FAILED' end", String.class).as(EXECUTION_STATUS_FIELD),
                        field("array_max(transform(taskexecutions, t -> t.executiontimeseconds))", Integer.class).as(EXECUTION_TIME_SECONDS_FIELD),
                        field("array_max(transform(taskexecutions, t -> t.memoryrequirementsgb))", Double.class).as(MEMORY_REQUIREMENTS_GB_FIELD),
                        field("array_max(transform(taskexecutions, t -> t.cpurequirements))", Integer.class).as(CPU_REQUIREMENTS_FIELD),
                        field("array_max(transform(taskexecutions, t -> t.cost))", String.class).as(COST_FIELD))
                        .from(taskExecutionsListNestedSelect)
                        .where(FILE_MODIFIED_TIME_ROW_NUM_FIELD.eq(inline(1))));

        return DSL.using(SQLDialect.DEFAULT, new Settings().withRenderFormatted(true))
                // Sub-query that flattens the runexecutions array for the partition
                .with(dedupedExecutionsTable)
                .with(dedupedRunExecutionsFromTasksTable)
                // Main query that uses the results of the subquery
                .select(this.selectFields)
                .from(select().from(dedupedExecutionsTable)
                        .unionAll(select().from(dedupedRunExecutionsFromTasksTable))
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
        return Set.of(min(field(getMetricColumnName())).as(getMinColumnName()),
                avg(field(getMetricColumnName(), Double.class)).as(getAvgColumnName()),
                max(field(getMetricColumnName())).as(getMaxColumnName()),
                count(field(getMetricColumnName())).as(getCountColumnName()));
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

    protected Optional<Integer> getCountColumnValue(QueryResultRow queryResultRow) {
        Optional<Integer> countColumnValue = queryResultRow.getColumnValue(getCountColumnName()).map(Integer::valueOf);
        if (countColumnValue.isPresent() && countColumnValue.get() == 0) { // There were 0 non-null column values
            return Optional.empty();
        }
        return countColumnValue;
    }
}
