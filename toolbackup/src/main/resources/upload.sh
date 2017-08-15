#!/bin/bash
java -jar /home/ubuntu/dockstore-support/toolbackup/target/client.jar --bucket-name clientbucket --key-prefix client --local-dir /mnt/saver/downloaded --test-mode-activate false
if [ $? -eq 0 ]; then
	curl -X POST --data-urlencode 'payload={"channel": "#dockstore", "username": "dockstoresaver", "text": "This is a message from dockstoresaver, looks like the backup worked"}' https://hooks.slack.com/services/CENSORED
else
	curl -X POST --data-urlencode 'payload={"channel": "#dockstore", "username": "dockstoresaver", "text": "This is a message from dockstoresaver, backup failed. Better check on what happened."}' https://hooks.slack.com/services/CENSORED
fi
