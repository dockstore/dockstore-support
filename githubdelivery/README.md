# GitHub Redelivery Module

This is a Java program that can be used to submit GitHub redelivery events to the webservice from the S3 bucket.

## Setup

### Configuration file

Create a configuration file like the following. A template `github-delivery.config` file can be found [here](templates/github-delivery.config).

```
[dockstore]
server-url: <Server URL>
token: <Dockstore curator or admin token>

[s3]
bucketName: <S3-bucket-name>
```
**Required:**
- `server-url`: The Dockstore server URL that's used to send API requests to.
  - Examples:
    - `https://qa.dockstore.org/api`
    - `https://staging.dockstore.org/api`
    - `https://dockstore.org/api`
- `token`: The Dockstore token of a Dockstore user. This user must be an admin or curator in order to be able to resubmit GitHub events to Dockstore.
- `bucketName`: The S3 bucket name storing GitHub events. This is the bucket that the client will go through in order
  to retrieve the events.

Note that if the configuration file path is not passed as an argument via `--config` or `-c`, then the default location is set to `./github-delivery.config`. 

### AWS credentials

This program requires AWS credentials that have permissions to read the GitHub events. There are several ways that this can be provided.
Read [this](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain) for the default credential provider chain.

## Running the program

```
Usage: <main class> [options] [command] [command options]
  Options:
    -c, --config
      The config file path.
      Default: ./github-delivery.config
    --help
      Prints help for githubdelivery
  Commands:
    submit-event      Submit a github event from S3 bucket using its key to 
            the webservice.
      Usage: submit-event [options]
        Options:
          -c, --config
            The config file path.
            Default: ./github-delivery.config
          -k, --key
            The key of the event in bucket. Format should be 
            YYYY-MM-DD/HH/deliveryid 

    submit-all      Submit all github events from S3 bucket from a specific 
            date to the webservice.
      Usage: submit-all [options]
        Options:
          -c, --config
            The config file path.
            Default: ./github-delivery.config
          -d, --date
            All events from the date. Format should be YYYY-MM-DD

    submit-hour      Submit all github events from S3 bucket from a specific 
            date and hour to the webservice.
      Usage: submit-hourly [options]
        Options:
          -c, --config
            The config file path.
            Default: ./github-delivery.config
          -k, --key
            Desired date and hour. Format should be YYYY-MM-DD/HH

```

### submit-event

**Using the default configuration file path:**

`java -jar target/githubdelivery-*-SNAPSHOT.jar submit-event --key <bucket key>`

**Using a custom configuration file path:**

`java -jar target/githubdelivery-*-SNAPSHOT.jar submit-event --config my-custom-config --key <bucket key>`

### submit-all

**Using the default configuration file path:**

```
`java -jar target/githubdelivery-*-SNAPSHOT.jar submit-all --date <date>`
```

### submit-hour

**Using the default configuration file path:**

```
`java -jar target/githubdelivery-*-SNAPSHOT.jar submit-hourly --key <key prefix>`
```

**Using a custom configuration file path:**

`java -jar target/githubdelivery-*-SNAPSHOT.jar submit-hourly --config my-custom-config --key <key prefix>`