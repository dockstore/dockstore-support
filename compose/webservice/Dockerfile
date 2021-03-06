FROM ubuntu:16.04

# Update the APT cache
# prepare for Java download
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y \
    python-software-properties \
    software-properties-common \
    telnet \
    && apt-get clean

# grab oracle java (auto accept licence)
RUN add-apt-repository -y ppa:webupd8team/java \
    && apt-get update \
    && echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections \
    && apt-get install -y oracle-java8-installer

# install Dockstore services
ARG dockstore_version=1.1

RUN wget --no-verbose https://seqwaremaven.oicr.on.ca/artifactory/collab-release/io/dockstore/dockstore-webservice/${dockstore_version}/dockstore-webservice-${dockstore_version}.jar

# install dockerize 
ENV DOCKERIZE_VERSION=v0.2.0

RUN wget --no-verbose https://github.com/jwilder/dockerize/releases/download/$DOCKERIZE_VERSION/dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz \
    && tar -C /usr/local/bin -xzvf dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz

# the config file for the webservice 
COPY web.yml .
ADD init_webservice.sh .

RUN chmod u+x init_webservice.sh
RUN mkdir /dockstore_logs && chmod a+rx /dockstore_logs

# Waiting for postgres
CMD ["dockerize", "-wait", "tcp://postgres:5432", "-timeout", "60s", "./init_webservice.sh"]
