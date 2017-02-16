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
server-url = https://dockstore.org:8443
endpoint = XXX
```
By default the token is empty. The default value for the server-url is shown above. These two values are for retrieving dockstore tools.Only the endpoint is mandatory.  You can set up your config like so:
```
endpoint = XXX
```

### AWS Credentials

If you do not have ~/.aws/credentials, during testing, the script will generate this file with only the default profile. The default profile is necessary for this script's tests. <b>If you have the credentials file but it is missing the default profile, you must add it in.</b>
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

### How it Works

Client will pull all the GA4GH tools from the server-url and save them locally. It will not pull a docker image again if its size has not changed. It will then detect which files need to be uploaded to OpenStack based on file sizes. However, the script will always upload report files in the report subdirectory. This subdirectory will be generated in the local directory that the user specified. For this example, /home/ubuntu/clientEx/report will be generated containing the HTML report and map.JSON.

### map.JSON
```
[
  {
    "toolname": "dockstore-tool-bamstats",
    "versions": [
      {
        "version": "develop",
        "metaVersion": "2016-11-11 18:17:46.0",
        "dockerSize": 541073189,
        "fileSize": 558081536,
        "valid": true,
        "timesOfExecution": [
          "10-02-2017 10:39:55",
          "10-02-2017 10:40:59"
        ],
        "path": ""
      },
      {
        "version": "develop",
        "metaVersion": "2016-11-11 18:17:46.0",
        "dockerSize": 681073489,
        "fileSize": 692073489,
        "valid": true,
        "timesOfExecution": [
          "10-02-2017 11:28:39"
        ],
        "path": "/home/kcao/clientEx/quay.io/collaboratory/dockstore-tool-bamstats/develop.tar"
      }
  }
]
```
Here is an example map.JSON file. It keeps track of tools and their versions and meta-versions. It also contains information about an image's size on docker and its file size when saved locally. If an image was not able to be pulled, its <b>valid</b> field would be false. The <b>timesOfExecution</b> tracks the times the script has executed and the version of this tool's version has remained the same. The <b>path</b> refers to the saved image's local file path. If the image changes, as shown here, there would be a new version object. If the modified image is valid, the old version will have an empty path. 

### HTML Report

The index.html is the main menu for all GA4GH tools. It also displays how many GBs have been added to cloud as well as how many GBs were previously on the cloud. The individual tool reports which can be accessed on index.html displays the information in map.JSON in a more readable format with the following columns:

- Version
- Meta-Version (API)
- Size (GB)
- Recent Executions
- Availability
- File Path

Please note that in <b>Recent Executions</b> in the report, it will show at most three times of execution. <b>Availability</b> is the same as <b>valid</b> in the JSON file.

## Downloader

This is the script to download images from OpenStack to the user's local file system.
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

