FROM eclipse-temurin:21.0.11_10-jdk-jammy

# Update the APT cache
# Prepare for Java download
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install unzip jq -y --no-install-recommends

# Explicitly upgrade libgnutls30 and verify it is at least the version fixing
# CVE-2026-42010 and CVE-2026-33845 (SEAB-7616). apt-get upgrade above
# already pulls the latest available libgnutls30, but this fails the build
# loudly if a cached/older archive would otherwise leave a vulnerable
# version in place instead of silently shipping it.
RUN apt-get update \
    && apt-get install -y --only-upgrade libgnutls30 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* \
    && dpkg --compare-versions "$(dpkg-query -W -f='${Version}' libgnutls30)" ge 3.7.3-4ubuntu1.9

# Explicitly upgrade curl/libcurl4 and verify they are at least the version fixing
# CVE-2026-18924, CVE-2026-82209, CVE-2026-80230 and CVE-2026-8927 (SEAB-7778).
# CVE-2026-9080 is not affecting jammy. As above, this fails the build loudly
# instead of silently shipping a vulnerable curl from a cached/older archive.
RUN apt-get update \
    && apt-get install -y --only-upgrade curl libcurl4 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* \
    && dpkg --compare-versions "$(dpkg-query -W -f='${Version}' curl)" ge 7.81.0-1ubuntu1.29 \
    && dpkg --compare-versions "$(dpkg-query -W -f='${Version}' libcurl4)" ge 7.81.0-1ubuntu1.29

# Install aws cli so dockstore-deploy can use the aws cli to upload files to S3
RUN curl -s "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
RUN unzip -q /tmp/awscliv2.zip -d /tmp
RUN /tmp/aws/install
RUN rm -f /tmp/awscliv2.zip
RUN rm -fr /tmp/aws

# Copy, for example, topicgenerator-1.16.0-SNAPSHOT.jar, but not topicgenerator-1.16.0-SNAPSHOT-sources.jar
COPY topicgenerator/target/topicgenerator*[^s].jar /home/topic-generator.jar

COPY metricsaggregator/target/metricsaggregator*[^s].jar /home/metrics-aggregator.jar

COPY categorizer/target/categorizer*[^s].jar /home/categorizer.jar

# Download the generated ontology JSON files from the dockstore/ontology repo
ARG ONTOLOGY_REF=1.0.0
RUN mkdir -p /home/ontology
RUN for f in operation.json topic.json input-format.json input-data.json output-format.json output-data.json; do \
        curl -sf "https://raw.githubusercontent.com/dockstore/ontology/${ONTOLOGY_REF}/generated/${f}" -o "/home/ontology/${f}"; \
    done
