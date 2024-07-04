package io.dockstore.metricsaggregator.helper;

import static java.util.stream.Collectors.groupingBy;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.cube;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
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
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import io.dockstore.openapi.client.model.ValidatorInfo;
import io.dockstore.openapi.client.model.ValidatorVersionInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.jooq.CommonTableExpression;
import org.jooq.Field;
import org.jooq.Record4;
import org.jooq.Record5;
import org.jooq.Record7;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

public class ValidationStatusAthenaAggregator extends AthenaAggregator<ValidationStatusMetric> {
    // Column names for validation statuses
    private static final Field<String> VALIDATOR_TOOL_FIELD = field("validatortool", String.class);
    private static final Field<String> VALIDATOR_TOOL_VERSION_FIELD = field("validatortoolversion", String.class);
    private static final Field<Boolean> IS_VALID_FIELD = field("isvalid", Boolean.class);
    private static final Field<String> ERROR_MESSAGE_FIELD = field("errormessage", String.class);
    // Calculated columns
    private static final Field<Integer> NUMBER_OF_RUNS_FIELD = field("numberofruns", Integer.class);
    private static final Field<Double> PASSING_RATE_FIELD = field("passingrate", Double.class);
    private static final Field<Integer> MOST_RECENT_ROW_NUM_FIELD = field("mostrecentrownum", Integer.class);

    public ValidationStatusAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName) {
        super(metricsAggregatorAthenaClient, tableName);
    }

    /**
     * Create the validationexecutions query string using the SELECT and GROUP BY fields.
     *
     * @return
     */
    @Override
    protected String createQuery(AthenaTablePartition partition) {
        final double oneHundredPercent = 100.0;
        // Names of the CTEs
        // A query that flattens the validationexecutions array
        final CommonTableExpression<Record7<String, String, String, String, Boolean, String, Integer>> executionsTable = name("executions")
                .fields(PLATFORM_FIELD.getName(), DATE_EXECUTED_FIELD.getName(), VALIDATOR_TOOL_FIELD.getName(), VALIDATOR_TOOL_VERSION_FIELD.getName(),
                        IS_VALID_FIELD.getName(), ERROR_MESSAGE_FIELD.getName(), MOST_RECENT_ROW_NUM_FIELD.getName())
                .as(select(PLATFORM_FIELD,
                            field("unnested." + DATE_EXECUTED_FIELD, String.class),
                            field("unnested." + VALIDATOR_TOOL_FIELD, String.class),
                            field("unnested." + VALIDATOR_TOOL_VERSION_FIELD, String.class),
                            field("unnested." + IS_VALID_FIELD, Boolean.class),
                            field("unnested." + ERROR_MESSAGE_FIELD, String.class),
                            rowNumber().over().orderBy(field("unnested." + DATE_EXECUTED_FIELD).desc()).as(MOST_RECENT_ROW_NUM_FIELD))
                            .from(table(tableName), unnest(field("validationexecutions", String[].class)).as("t", "unnested"))
                            .where(ENTITY_FIELD.eq(inline(partition.entity()))
                                    .and(REGISTRY_FIELD.eq(inline(partition.registry())))
                                    .and(ORG_FIELD.eq(inline(partition.org())))
                                    .and(NAME_FIELD.eq(inline(partition.name())))
                                    .and(VERSION_FIELD.eq(inline(partition.version())))));
        // A query that calculates the number of runs and passing rate grouped by platform, validatortool, and validatortoolversion
        final CommonTableExpression<Record5<String, String, String, Integer, Integer>> validatorMetricsTable = name("validatormetrics")
                .fields(PLATFORM_FIELD.getName(),
                        VALIDATOR_TOOL_FIELD.getName(),
                        VALIDATOR_TOOL_VERSION_FIELD.getName(),
                        NUMBER_OF_RUNS_FIELD.getName(),
                        PASSING_RATE_FIELD.getName())
                .as(select(coalesce(PLATFORM_FIELD, inline(Partner.ALL.name())), VALIDATOR_TOOL_FIELD,
                        coalesce(VALIDATOR_TOOL_VERSION_FIELD, inline(Partner.ALL.name())),
                        count(),
                        count().filterWhere(IS_VALID_FIELD).multiply(inline(oneHundredPercent)).divide(count()))
                        .from(executionsTable)
                        .groupBy(cube(PLATFORM_FIELD, VALIDATOR_TOOL_VERSION_FIELD), VALIDATOR_TOOL_FIELD));
        // A query that identifies the mostrecentrownum grouped by platform, validatortool, and validatortoolversion
        final CommonTableExpression<Record4<String, String, String, Integer>> mostRecentExecutionsTable = name("mostrecentexecutions")
                .fields(PLATFORM_FIELD.getName(),
                        VALIDATOR_TOOL_FIELD.getName(),
                        VALIDATOR_TOOL_VERSION_FIELD.getName(),
                        MOST_RECENT_ROW_NUM_FIELD.getName())
                .as(select(coalesce(PLATFORM_FIELD, inline(Partner.ALL.name())),
                        VALIDATOR_TOOL_FIELD,
                        coalesce(VALIDATOR_TOOL_VERSION_FIELD, inline(Partner.ALL.name())),
                        min(MOST_RECENT_ROW_NUM_FIELD))
                        .from(executionsTable)
                        .groupBy(cube(PLATFORM_FIELD, VALIDATOR_TOOL_VERSION_FIELD), VALIDATOR_TOOL_FIELD));
        return DSL.using(SQLDialect.DEFAULT, new Settings().withRenderFormatted(true))
                .with(executionsTable)
                .with(validatorMetricsTable)
                .with(mostRecentExecutionsTable)
                // Main query that gets the numberofruns, passing rate, and most recent validator tool version info for each group of platforn and validator tool
                .select(validatorMetricsTable.field(PLATFORM_FIELD),
                        validatorMetricsTable.field(VALIDATOR_TOOL_FIELD),
                        validatorMetricsTable.field(VALIDATOR_TOOL_VERSION_FIELD),
                        validatorMetricsTable.field(NUMBER_OF_RUNS_FIELD),
                        validatorMetricsTable.field(PASSING_RATE_FIELD),
                        executionsTable.field(DATE_EXECUTED_FIELD),
                        executionsTable.field(VALIDATOR_TOOL_VERSION_FIELD).as("mostrecentvalidatortoolversion"),
                        executionsTable.field(IS_VALID_FIELD),
                        executionsTable.field(ERROR_MESSAGE_FIELD))
                .from(validatorMetricsTable)
                .innerJoin(mostRecentExecutionsTable)
                .on(validatorMetricsTable.field(PLATFORM_FIELD).eq(mostRecentExecutionsTable.field(PLATFORM_FIELD))
                        .and(validatorMetricsTable.field(VALIDATOR_TOOL_FIELD).eq(mostRecentExecutionsTable.field(VALIDATOR_TOOL_FIELD)))
                        .and(validatorMetricsTable.field(VALIDATOR_TOOL_VERSION_FIELD).eq(mostRecentExecutionsTable.field(VALIDATOR_TOOL_VERSION_FIELD))))
                .innerJoin(executionsTable)
                .on(mostRecentExecutionsTable.field(MOST_RECENT_ROW_NUM_FIELD).eq(executionsTable.field(MOST_RECENT_ROW_NUM_FIELD)))
                .getSQL();
    }

    /**
     * Given a list of query result rows, creates a metric for each platform.
     * @param queryResultRows
     * @return
     */
    @Override
    protected Map<String, ValidationStatusMetric> createMetricByPlatform(List<QueryResultRow> queryResultRows) {
        Map<String, ValidationStatusMetric> metricByPlatform = new HashMap<>();
        // Group query results by platform
        Map<String, List<QueryResultRow>> queryResultRowsByPlatform = queryResultRows.stream()
                .filter(row -> getPlatformFromQueryResultRow(row).isPresent())
                .collect(groupingBy(row -> getPlatformFromQueryResultRow(row).get()));

        // For each platform, create a metric
        for (Entry<String, List<QueryResultRow>> queryResultRowsForPlatformEntry : queryResultRowsByPlatform.entrySet()) {
            Map<String, ValidatorInfo> validatorInfoByValidatorTools = new HashMap<>();
            final String platform = queryResultRowsForPlatformEntry.getKey();
            final List<QueryResultRow> queryResultRowsForPlatform = queryResultRowsForPlatformEntry.getValue();

            Map<String, List<QueryResultRow>> queryResultRowsByValidatorTool = queryResultRowsForPlatform.stream()
                    .filter(row -> row.getColumnValue(VALIDATOR_TOOL_FIELD).isPresent())
                    .collect(groupingBy(row -> row.getColumnValue(VALIDATOR_TOOL_FIELD).get()));

            // For each validator tool executed on the platform, create a ValidatorInfo object
            for (Entry<String, List<QueryResultRow>> queryResultRowsByValidatorToolEntry : queryResultRowsByValidatorTool.entrySet()) {
                final String validatorTool = queryResultRowsByValidatorToolEntry.getKey();
                final List<QueryResultRow> queryResultRowsForValidatorTool = queryResultRowsByValidatorToolEntry.getValue();
                ValidatorInfo validatorInfo = getValidatorInfo(queryResultRowsForValidatorTool);
                validatorInfoByValidatorTools.put(validatorTool, validatorInfo);
            }

            ValidationStatusMetric validationStatusMetricForPlatform = new ValidationStatusMetric().validatorTools(validatorInfoByValidatorTools);
            metricByPlatform.put(platform, validationStatusMetricForPlatform);
        }
        return metricByPlatform;
    }

    /**
     * For a list of rows containing information about a validator tool, create a ValidatorInfo object
     * @param queryResultRowsForValidatorTool
     * @return
     */
    private static ValidatorInfo getValidatorInfo(List<QueryResultRow> queryResultRowsForValidatorTool) {
        ValidatorInfo validatorInfo = new ValidatorInfo();
        List<ValidatorVersionInfo> validatorVersionInfoList = new ArrayList<>();
        queryResultRowsForValidatorTool.forEach(queryResultRow -> {
            final String validatorToolVersion = queryResultRow.getColumnValue(VALIDATOR_TOOL_VERSION_FIELD).orElse(null);
            final Integer numberOfRuns = queryResultRow.getColumnValue(NUMBER_OF_RUNS_FIELD).map(Integer::valueOf).orElse(null);
            final Double passingRate = queryResultRow.getColumnValue(PASSING_RATE_FIELD).map(Double::valueOf).orElse(null);

            if ("ALL".equals(validatorToolVersion)) { // Metrics over all versions, like overall number of runs
                validatorInfo.setNumberOfRuns(numberOfRuns);
                validatorInfo.setPassingRate(passingRate);
                validatorInfo.setMostRecentVersionName(
                        queryResultRow.getColumnValue("mostrecentvalidatortoolversion").orElse(null));
            } else {
                ValidatorVersionInfo validatorVersionInfo = new ValidatorVersionInfo().name(validatorToolVersion)
                        .numberOfRuns(numberOfRuns).passingRate(passingRate)
                        .isValid(queryResultRow.getColumnValue(IS_VALID_FIELD).map(Boolean::valueOf).orElse(null))
                        .dateExecuted(queryResultRow.getColumnValue(DATE_EXECUTED_FIELD).orElse(null))
                        .errorMessage(queryResultRow.getColumnValue(ERROR_MESSAGE_FIELD).orElse(null));
                validatorVersionInfoList.add(validatorVersionInfo);
            }
        });
        validatorInfo.setValidatorVersions(validatorVersionInfoList);
        return validatorInfo;
    }
}
