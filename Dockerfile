FROM eclipse-temurin:21.0.2_13-jdk-jammy

# Update the APT cache
# Prepare for Java download
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends
COPY topicgenerator/target/topicgenerator*[^s].jar /home/topic-generator.jar
