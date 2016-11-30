FROM ubuntu:16.04

# Update the APT cache
# prepare for Java download
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y \
    python-software-properties \
    software-properties-common \
    nginx \
    curl \
    git \
    wget \
    ruby ruby-dev make \
    && apt-get clean

ADD init_ui.sh .

RUN chmod u+x init_ui.sh \
    && apt-get install -y npm 

ENV NVM_DIR /usr/local/nvm
ENV NODE_VERSION 7.2.0

RUN rm /bin/sh && ln -s /bin/bash /bin/sh

WORKDIR /home/root
ARG dockstore_version=1.1.1                                                                                                                                                                                         
RUN git clone https://github.com/ga4gh/dockstore-ui.git
WORKDIR /home/root/dockstore-ui
RUN git checkout $dockstore_version

COPY webservice.js.template /home/root/dockstore-ui/app/scripts/services/webservice.js

# Install nvm with node and npm
RUN curl https://raw.githubusercontent.com/creationix/nvm/v0.32.0/install.sh | bash \
     && source $NVM_DIR/nvm.sh \
     && nvm install $NODE_VERSION \
     && nvm alias default $NODE_VERSION \
     && nvm use default \
     && node --version \
     && npm --version \
     && nvm --version \
     && npm install --global grunt-cli bower yo generator-karma generator-angular \ 
     && gem install compass \
     && npm install grunt --save-dev \
     && npm update

ENV NODE_PATH $NVM_DIR/v$NODE_VERSION/lib/node_modules
ENV PATH /usr/local/nvm/versions/node/v$NODE_VERSION/bin/node:$NVM_DIR/v$NODE_VERSION/bin:$PATH
ENV NODE_PATH=$NODE_PATH:/usr/local/nvm/versions/node/v$NODE_VERSION/lib/node_modules

# have to do this weird fix due to https://github.com/npm/npm/issues/9863
RUN  source  $NVM_DIR/nvm.sh \ 
     && mv ./node_modules ./node_modules.tmp && mv ./node_modules.tmp ./node_modules \
     && bower --allow-root update \
     && grunt

# install dockerize                                                                                                                                                                                                 
ENV DOCKERIZE_VERSION v0.2.0                                                                                                                                                                                        
RUN wget --no-verbose https://github.com/jwilder/dockerize/releases/download/$DOCKERIZE_VERSION/dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz \                                                                   
    && tar -C /usr/local/bin -xzvf dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz              

COPY nginx.template /etc/nginx/sites-available/nginx
COPY nginx.template /etc/nginx/sites-enabled/nginx

RUN rm /etc/nginx/sites-enabled/default && rm /etc/nginx/sites-available/default

CMD ["dockerize", "-wait", "tcp://webservice:8080", "-timeout", "60s", "/init_ui.sh"]
