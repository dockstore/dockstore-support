FROM ubuntu:16.04

# Update the APT cache
# prepare for Java download
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y \
    python-software-properties \
    software-properties-common \
    tree \
    vim \
    sudo \
    less \
    telnet \
    postgresql-client \
    && apt-get clean

# grab oracle java (auto accept licence)
RUN add-apt-repository -y ppa:webupd8team/java \
    && apt-get update \
    && echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections \
    && apt-get install -y oracle-java8-installer


RUN useradd -ms /bin/bash ubuntu 
# the web and Consonance config
WORKDIR /home/ubuntu

# install dockerize 
ENV DOCKERIZE_VERSION v0.2.0

RUN wget --no-verbose https://github.com/jwilder/dockerize/releases/download/$DOCKERIZE_VERSION/dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz \
    && tar -C /usr/local/bin -xzvf dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz


# TODO: make sure you create these from the .template files and customize them
RUN mkdir -p /home/ubuntu/.dockstore /home/ubuntu/.dockstore/self-installs /home/ubuntu/.ssh /home/ubuntu/bin \
    && chown -R ubuntu /home/ubuntu/

COPY config /home/ubuntu/.dockstore/config
COPY init_client.sh /home/ubuntu/init_client.sh

#RUN chmod 600 /home/ubuntu/.ssh/key.pem 
RUN chmod a+wrx /home/ubuntu/init_client.sh
RUN chown -R ubuntu /home/ubuntu 
USER ubuntu
RUN echo "postgres:5432:*:*:postgres" > /home/ubuntu/.pgpass && chmod 600 /home/ubuntu/.pgpass


# install Dockstore CLI services
ARG dockstore_version=1.1

# for dockstore command line on the main box 
RUN wget --no-verbose https://seqwaremaven.oicr.on.ca/artifactory/collab-release/io/dockstore/dockstore-client/${dockstore_version}/dockstore-client-${dockstore_version}.jar \
    && mv dockstore-client-${dockstore_version}.jar /home/ubuntu/.dockstore/self-installs/ \
    && wget https://github.com/ga4gh/dockstore/releases/download/${dockstore_version}/dockstore \
    && mv dockstore /home/ubuntu/bin \
    && chmod a+x /home/ubuntu/bin/dockstore

ENV PATH="/home/ubuntu/bin:${PATH}"

CMD ["dockerize", "-wait", "tcp://webservice:8080", "-timeout", "60s", "/home/ubuntu/init_client.sh"]
