# Metrics Aggregator

This is a Java program that aggregates metrics from S3 and puts the results into the webservice.

## Setup

### Configuration file

Create a configuration file like the following:

```
[dockstore]
server-url: <Dockstore server url>
token: <Dockstore token>

[s3]
bucketName: <S3 metrics bucket name>
endpointOverride: <Optional S3 endpoint override>
```
**Required:**
- `server-url`: The Dockstore server URL that's used to send API requests to.
- `token`: The Dockstore token of a Dockstore user. This user must be an admin or curator in order to be able to post aggregated metrics to Dockstore.
- `bucketName`: The S3 bucket name storing metrics data. This is the bucket that the metrics aggregator will go through in order
  to aggregate metrics.

**Optional:**
- `endpointOverride`: Endpoint override to use when creating the S3 clients. This is typically only used for local testing so that a LocalStack endpoint 
override is used.

### AWS credentials

This program requires AWS credentials that have permissions to read the S3 metrics bucket. There are several ways that this can be provided.
Read [this](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain) for the default credential provider chain.

## Running the program

```
Usage: <main class> [options] [command] [command options]
  Options:
    --help
      Prints help for metricsaggregator
  Commands:
    aggregate-metrics      Aggregate metrics in S3
      Usage: aggregate-metrics [options]
        Options:
          -c, --config
            The config file path.
            Default: ./metrics-aggregator.config
          --help
            Prints help for metricsaggregator
```

**Using the default configuration file path:**

`java -jar target/metricsaggregator-0.1-alpha.0-SNAPSHOT.jar aggregate-metrics`

**Using a custom configuration file path:**

`java -jar target/metricsaggregator-0.1-alpha.0-SNAPSHOT.jar aggregate-metrics --config my-custom-config`

