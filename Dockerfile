FROM eclipse-temurin:21.0.2_13-jdk-jammy

# Update the APT cache
# Prepare for Java download
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install unzip jq -y --no-install-recommends

# Install aws cli so dockstore-deploy can use the aws cli to upload files to S3
RUN curl -s "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
RUN unzip -q /tmp/awscliv2.zip -d /tmp
RUN /tmp/aws/install
RUN rm -f /tmp/awscliv2.zip
RUN rm -fr /tmp/aws

# Copy, for example, topicgenerator-1.16.0-SNAPSHOT.jar, but not topicgenerator-1.16.0-SNAPSHOT-sources.jar
COPY topicgenerator/target/topicgenerator*[^s].jar /home/topic-generator.jar

COPY metricsaggregator/target/metricsaggregator*[^s].jar /home/metrics-aggregator.jar
