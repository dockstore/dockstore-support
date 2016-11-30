#!/bin/bash

java -jar dockstore-webservice-*.jar server web.yml | tee /dockstore_logs/webservice.out
