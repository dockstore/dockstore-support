FROM eclipse-temurin:21.0.2_13-jdk-jammy

# Update the APT cache
# Prepare for Java download
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends
# Copy, for example, topicgenerator-1.16.0-SNAPSHOT.jar, but not topicgenerator-1.16.0-SNAPSHOT-sources.jar
COPY topicgenerator/target/topicgenerator*[^s].jar /home/topic-generator.jar
