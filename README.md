# dockstore-support

This repo is a sandbox for support code for running, testing workflows on Dockstore, and indexing GA4GH tool registries. 
Send issues to the main dockstore repo.

## Prerequisites

Your environment needs to have the following items:

* [Maven](https://maven.apache.org/)
* [Docker](https://www.digitalocean.com/community/tutorials/how-to-install-and-use-docker-on-ubuntu-16-04) 

Before you can run the script, you must generate the jar files.
```
cd toolbackup && mvn clean install
```
After you have installed Maven and Docker, you may wish to run the tests; in which case, you need S3Proxy.
```
docker pull andrewgaul/s3proxy
docker run -d --publish 8080:80 --env S3PROXY_AUTHORIZATION=none andrewgaul/s3proxy
```
## Communicating with OpenStack

### Endpoint Configuration

To use the script, you must provide an endpoint in ~/.toolbackup/config.ini
```
token = XXX
server-url = XXX
endpoint = XXX
```
Only endpoint is mandatory. The others are for retrieving dockstore tools. You can set up your config like so:
```
endpoint = XXX
```

### AWS Credentials

The tests will generate an example config file for you as ~/.aws/credentials with only the default profile if you do not have this file already.
```
[default]
aws_access_key_id=MOCK_ACCESS_KEY
aws_secret_access_key=MOCK_SECRET_KEY
```
You must supply the file with a dockstore profile and the proper keys.
```
[default]
aws_access_key_id=MOCK_ACCESS_KEY
aws_secret_access_key=MOCK_SECRET_KEY

[dockstore]
aws_access_key_id=MOCK_ACCESS_KEY
aws_secret_access_key=MOCK_SECRET_KEY
```

## Client

This is the script to backup dockstore images from quay.io into Openstack
```
java -jar target/client.jar --bucket-name clientbucket --key-prefix client --local-dir /home/ubuntu/clientEx --test-mode-activate true
```
We are running with test mode activated which means we will not download all dockstore images. The dockstore images targeted will be stored on Openstack in the bucket <b>clientbucket</b> and in the key-prefix <b>client</b> within the bucket. The bucket and key-prefix need not have been created. The directory, <b>/home/ubuntu/clientEx</b> will act as temporary storage and it need not to have already been created. 

## Downloader

This is the script to backup dockstore images from quay.io into Openstack
```
java -jar target/downloader.jar --bucket-name clientbucket --key-prefix client --destination-dir /home/ubuntu/downloaderEx
```
We are downloading everything in the key-prefix <b>client</b> within the bucket <b>clientbucket</b> into a directory that need not have already been created, <b>/home/ubuntu/downloaderEx</b>.

## Tests

The tests do not require a configuration file, but if you wish to set up the values yourself, you can add to the aforementioned ~/.toolbackup/config.ini
The default values are shown here.
```
bucket = testbucket
prefix = testprefix
img = docker/whalesay
baseDir = /home/ubuntu/dockstore-saver
dir = /home/ubuntu/dockstore-saver/dir
checkSizeDir = /home/ubuntu/dockstore-saver/checkSize

[nonexistent]
bucket = dockstore-saver-gibberish
dir = dockstore-saver-gibberish
img = dockstore-saver-gibberish
```
- bucket: Amazon bucket you wish to use for testing the client and downloader
- prefix: Consider it a "subdirectory" of the bucket
- img: A valid image that can be pulled by any environment which has Docker
- baseDir: A local directory which <b>will not be deleted</b>
- dir: A local directory which <b>will be deleted</b>, if not specified it will be ~/...baseDir.../dir
- checkSizeDir: A local directory to test that the calculation of files' sizes is correct
- nonexistent.bucket: A non-existent bucket
- nonexistent.dir: A non-existent local directory
- nonexistent.img: A non-existent Docker image

The tests will clean up everything but the baseDir. <b>It would be best if you specify directories which do not currently exist, even for the baseDir.</b>

