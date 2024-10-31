package io.dockstore.metricsaggregator.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.QueryExecutionContext;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;

/**
 * A helper class for executing queries with the AthenaClient.
 */
public final class AthenaClientHelper {
    public static final long SLEEP_AMOUNT_IN_MS = 500;

    private static final Logger LOG = LoggerFactory.getLogger(AthenaClientHelper.class);

    private AthenaClientHelper() {
    }

    public static AthenaClient createAthenaClient() {
        return AthenaClient.builder().credentialsProvider(DefaultCredentialsProvider.create()).build();
    }

    /**
     * Executes an Athena query and returns GetQueryResultsIterable containing the query results
     * @param athenaClient
     * @param athenaDatabase
     * @param athenaWorkgroup
     * @param query
     * @return
     * @throws Exception
     */
    public static GetQueryResultsIterable executeQuery(AthenaClient athenaClient, String athenaDatabase, String athenaWorkgroup, String query)
            throws AwsServiceException, SdkClientException, InterruptedException {
        String queryExecutionId = submitAthenaQuery(athenaClient, athenaDatabase, athenaWorkgroup, query);
        waitForQueryToComplete(athenaClient, queryExecutionId);
        return getQueryResults(athenaClient, queryExecutionId);
    }

    /**
     * Submits a query to Amazon Athena and returns the execution ID of the query.
     * @param athenaClient
     * @param athenaDatabase
     * @param athenaWorkgroup
     * @param query
     * @return Athena query execution ID
     */
    public static String submitAthenaQuery(AthenaClient athenaClient, String athenaDatabase, String athenaWorkgroup, String query) throws AwsServiceException, SdkClientException {
        // The QueryExecutionContext allows us to set the database.
        QueryExecutionContext queryExecutionContext = QueryExecutionContext.builder()
                .database(athenaDatabase)
                .build();

        StartQueryExecutionRequest startQueryExecutionRequest = StartQueryExecutionRequest.builder()
                .workGroup(athenaWorkgroup)
                .queryString(query)
                .queryExecutionContext(queryExecutionContext)
                .build();

        StartQueryExecutionResponse startQueryExecutionResponse = athenaClient.startQueryExecution(startQueryExecutionRequest);
        return startQueryExecutionResponse.queryExecutionId();
    }

    /**
     * Wait for an Amazon Athena query to complete, fail or to be cancelled.
     * @param athenaClient
     * @param queryExecutionId
     * @throws InterruptedException
     */
    public static void waitForQueryToComplete(AthenaClient athenaClient, String queryExecutionId) throws InterruptedException, AwsServiceException, SdkClientException  {
        GetQueryExecutionRequest getQueryExecutionRequest = GetQueryExecutionRequest.builder()
                .queryExecutionId(queryExecutionId)
                .build();

        GetQueryExecutionResponse getQueryExecutionResponse;
        boolean isQueryStillRunning = true;
        while (isQueryStillRunning) {
            getQueryExecutionResponse = athenaClient.getQueryExecution(getQueryExecutionRequest);
            QueryExecutionState queryState = getQueryExecutionResponse.queryExecution().status().state();
            if (queryState == QueryExecutionState.FAILED) {
                throw new RuntimeException(
                        "The Amazon Athena query failed to run with error message: " + getQueryExecutionResponse
                                .queryExecution().status().stateChangeReason());
            } else if (queryState == QueryExecutionState.CANCELLED) {
                throw new RuntimeException("The Amazon Athena query was cancelled.");
            } else if (queryState == QueryExecutionState.SUCCEEDED) {
                isQueryStillRunning = false;
            } else {
                // Sleep an amount of time before retrying again.
                Thread.sleep(SLEEP_AMOUNT_IN_MS);
            }
        }
    }

    public static GetQueryResultsIterable getQueryResults(AthenaClient athenaClient, String queryExecutionId) {
        GetQueryResultsRequest getQueryResultsRequest = GetQueryResultsRequest.builder()
                .queryExecutionId(queryExecutionId)
                .build();
        return athenaClient.getQueryResultsPaginator(getQueryResultsRequest);
    }
}
