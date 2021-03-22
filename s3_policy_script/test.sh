#!/usr/bin/env bash
set -o errexit
set -o pipefail
set -o nounset

# Requires awcli
# pip install awscli --user --upgrade
# https://docs.aws.amazon.com/cli/latest/userguide/installing.html
BUCKETS=`aws s3api list-buckets --query 'Buckets[*].Name' --output text | tr " " "\n"`
mkdir -p gen
for BUCKET in $BUCKETS
do
    echo $BUCKET
    aws s3api get-bucket-policy --bucket $BUCKET --query Policy --output text > gen/original.$BUCKET.policy.json 2> /dev/null || true
    # we want to do this if there is no original policy
    if [ ! -s gen/original.$BUCKET.policy.json ]
    then	    
      echo "updating $BUCKET"
      echo "{ \"name\":\"$BUCKET\"}" > gen/$BUCKET.temp.hash
      mustache gen/$BUCKET.temp.hash limit_ssl.template > gen/$BUCKET.temp.policy.json
      aws s3api put-bucket-policy --bucket $BUCKET --policy file://gen/$BUCKET.temp.policy.json
    fi
done
