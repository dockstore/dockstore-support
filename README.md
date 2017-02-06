# dockstore-support

This repo is a sandbox for support code for running, testing workflows on Dockstore, and indexing GA4GH tool registries. 
Send issues to the main dockstore repo.

## Prerequisites
Your environment needs to have the following items:

* [Maven](https://maven.apache.org/)
* [Docker](https://www.digitalocean.com/community/tutorials/how-to-install-and-use-docker-on-ubuntu-16-04) 

## Running dockstore-support

After you have installed Maven and Docker, the first thing to do before running the project is to set up s3proxy.
```
docker pull andrewgaul/s3proxy
docker run -d --publish 8080:80 --env S3PROXY_AUTHORIZATION=none andrewgaul/s3proxy
```
Before you can run the script, you must generate the jar files.
```
cd toolbackup && mvn clean install
```
### Set up AWS Credentials
The tests will generate an example config file for you as ~/.aws/credentials
```
[default]
aws_access_key_id=MOCK_ACCESS_KEY
aws_secret_access_key=MOCK_SECRET_KEY
```
You should replace the values with the ones you are actually using. 
### Client
This is the script to backup dockstore images from quay.io into Openstack
```
java -jar target/client.jar --bucket-name clientbucket --key-prefix client --local-dir /home/ubuntu/clientEx --test-mode-activate true
```
We are running with test mode activated which means we will not download all dockstore images. The dockstore images targeted will be stored on Openstack in the bucket <b>clientbucket</b> and in the key-prefix <b>client</b> within the bucket. The bucket and key-prefix need not have been created. The directory, <b>/home/ubuntu/clientEx</b> will act as temporary storage and it need not to have already been created. 

### Downloader
This is the script to backup dockstore images from quay.io into Openstack
```
java -jar target/downloader.jar --bucket-name clientbucket --key-prefix client --destination-dir /home/ubuntu/downloaderEx
```
We are downloading everything in the key-prefix <b>client</b> within the bucket <b>clientbucket</b> into a directory that need not have already been created, <b>/home/ubuntu/downloaderEx</b>.
