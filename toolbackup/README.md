# dockstore-support/toolbackup
This README is to transfer the knowledge to anyone assigned 
with the ticket pertaining to calculating the DockerHub image sizes
that Dockstore references. This README will cover the following:

* How to run the script
* Issues that emerged when creating the script

# Prerequisites
Before you run the script, your environment must have the follow items:

* [Maven](https://maven.apache.org/)
* [Docker](https://www.digitalocean.com/community/tutorials/how-to-install-and-use-docker-on-ubuntu-16-04)

You must also provide an endpoint in `~/.toolbackup/config.ini`

```
token = XXX
server-url = https://dockstore.org:8443
endpoint = XXX
```

You do not need to AWS Credentials as this script does not require it.
However, if by chance that you need to modify this script to utilize
AWS, please refer to the README in the dockstore-support repository

# Running the script
After setting up your environment, you must first build the project with
the up-to-date changes by running a Maven build:

```
mvn clean install -DskipTests
```

Note that you do not need to run the tests as they will most definitely fail.
The reason behind the tests failing is that Maven will test the S3Communicator,
which is left out of this script.

After running a Maven build, run the following command to start up the
script:

```
java -jar target/client.jar --bucket-name clientbucket --key-prefix client --local-dir <home-directory>/clientEx --test-mode-activate true
```

Note that the home-directory is your local machine's actual home directory.
* For Ubuntu users, it is: `/home/ubuntu/`
* For MacOS users, it is: `/Users/<Your User Name>`

The script should now be running. Keep in mind that it would take approximately
2 hours and 30 minutes for the script to run through every Dockstore image
and retrieve the size from the DockerHub API.

Note that during and after the script has finished running, 8 csv files will be generated
in relative path of the script. The generated csv files are:

* imagesRetrievedFromDockerHub.csv
* imagesFailedToRetrieveFromDockerHub.csv
* dockstoreToolsWith204Response.csv
* dockstoreToolsWithEmptyJSONObject.csv
* dockstoreImageWithNoTags.csv
* possibleInvalidDockerParameter.csv
* workflowVersionsWith500Response.csv
* nonDockerHubImage.csv

# Issues that were run into
1. One of the main issues is that our Dockstore API call `/workflows/published/{workflowId}`
   returned either a 204 response (Empty response), or an empty JSON object for some workflowIds.
   The issue has been logged here:
   
   * https://github.com/dockstore/dockstore/issues/3840
   
   Frankly, once the issue with the tools tab is resolved then the script should be able to automatically
   count up the additional DockerHub images that were held back by the bug.

2. Another big issue is that the V2 DockerHub API returns two size parameters:
    * `full_size`
    * `size`

    `full_size` is affiliated with the entire JSON object that the API returns while
    `size` is only affiliated with the `images` parameter in the returned JSON object.
    There are two issues at hand with this:
    
    1. We don't know the differentiation between `size` and `full_size`. At the time
       of writing this document, we can only speculate that `full_size` is the compressed 
       sum of all the `size` parameters under `images`. Unfortunately DockerHub has not
       provided any documentation for the mentioned parameters.
    2. `full_size` and `size` do not add up at times. For example, take a look at this
        API response: 
        
        * https://hub.docker.com/v2/repositories/library/python/tags/2.7?page_size=10
        
        As displayed, the `full_size` parameter returns roughly about 345MB whereas
        the sum of the `size` parameters exceeds 345MB. This leads to the theory that
        since image layers can be shared, `full_size` does not count the repeated parts
        of the image. Again, the issue is that DockerHub has not documented any of the
        parameters, and there has not been any luck with finding the information from
        outside sources so there is no way of knowing for certain.
        
        As of now, the script has been written to count the `full_sze`, and in the case
        that `full_size` is 0, then the script would count up all of the `size` under
        `images`.
        
        Another noteworthy API Response to mention is:
        
        * https://hub.docker.com/v2/repositories/winterfelldream2016/expressionpipe/tags/beta_gc_bias_correct?page_size=10
        
        Notice how that the `size` under `image` is 0, but the `full_size` is about 280MB. That is one
        thing that we have yet to figure out. Going to the actual DockerHub link
        also leads to an error in the console. 
        
        * https://registry.hub.docker.com/layers/winterfelldream2016/expressionpipe/beta_gc_bias_correct/images/?context=explore
        
        Most likely this is caused by an issue in DockerHub itself. It is unlikely DockerHub would
        intentionally cause this blank page to appear else they would do something to prevent users from
        navigating to the page.
        
3. There is an issue with some workflow versions' descriptor files referencing a variable in the `docker` parameter.
   Since the `docker` parameter only takes in a string, this leads to the parameter referencing the variable name
   rather than the variable data itself. The issue has been logged here:
   
   * https://github.com/dockstore/dockstore/issues/3822
   
4. There are some workflow versions that return 500 errors. Keep in mind that each 500 error
   may not be the same, and that you will have to look through each of them from the generated csv
   file: 
   
   * workflowVersionsWith500Response.csv
   
   As shown in this Dockstore API Method:
   
   * https://dev.dockstore.net/api/workflows/5705/tools/18676
   
   You can see that the response is the error message. There will be
   many more messages similar to this one, but also many other different 
   messages. It's best to look over the csv file and create an issue for the
   different errors, though it will take some time.
   
5. There are some workflow versions that are missing a version in their
   `docker` parameter. Without a version, we have no way of knowing which
   DockerHub image to fetch. For example:
   
   * https://dockstore.org/workflows/github.com/broadinstitute/gatk/run_happy:4.1.6.0?tab=tools
   
   The workflow version references a valid `docker` parameter path, but it is missing a version
   which should be attached at the end of the path. Here is an example of a valid `docker`
   parameter.
   
   * https://dockstore.org/workflows/github.com/nf-core/hlatyping:1.1.2?tab=tools
   
   You can see that there is a version attached at the end of the `docker` path.


