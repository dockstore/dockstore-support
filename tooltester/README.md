Last tested with ansible 2.2.1.0

The general idea is to only have the `jenkins` user talk to each other, never using the `ubuntu` user

# Master Setup:
1. Create a c2.micro flavor Ubuntu 18.04 image on Collaboratory
1. Give it the security groups: Jenkins, Default
1. Run the setupMaster.sh
1. Log out and log back in

Follow these steps if there is a backup

---
1. Configure the aws cli using `aws configure`. Ask around for the credentials (~/.aws/credentials)
1. aws s3 --endpoint-url https://object.cancercollaboratory.org:9080 cp s3://dockstore/jenkinsMaster2/jenkins_home.tar.gz jenkins_home.tar.gz
1. Extract with `tar xvzf jenkins_home.tar.gz` and then remove the far file
1. Run the jenkins container
    `docker run -u root --rm -d -p 8080:8080 -p 50000:50000 -v $PWD/jenkins_home:/var/jenkins_home -v /var/run/docker.sock:/var/run/docker.sock jenkinsci/blueocean:1.13.1`
1. Make the .ssh directory and copy the id_rsa into it
---

Follow these setups if there is not a backup
1. Run the jenkins container
    `docker run -u root --rm -d -p 8080:8080 -p 50000:50000 -v $PWD/jenkins_home:/var/jenkins_home -v /var/run/docker.sock:/var/run/docker.sock jenkinsci/blueocean:1.13.1`
1. Create a pipeline called PipelineTest
1. Copy the contents of resources/PipelineTest.groovy into the Pipeline Script textbox
1. Check the checkbox:  "This project is parameterized"
1. Create the String Parameters mentioned in the [constructParameterMap](https://github.com/ga4gh/dockstore-support/blob/develop/tooltester/src/main/java/io/dockstore/tooltester/client/cli/Client.java#L609) function. These string parameters are used to configure the Jenkins pipelines to run the correct tool/workflow, versions, etc.
1. Make sure "Use Groovy Sandbox" is NOT checked
1. Change master node to not be used (0 executors)
1. Make the .ssh directory and copy the id_rsa into it

# Slave Setup:
1. Create a c2.large flavor Ubuntu 18.04 image on Collaboratory and give it the JenkinsMaster2 key pair
1. Run the setupSlave.sh script
1. Configure the aws cli using `sudo -u jenkins -i` and then `aws configure`. Use the credentials in the jenkins@jenkins-master's ~/.aws/credentials
1. Configure slave on master Jenkin: Manage Jenkins => Manage Nodes => New Node => Permanent Agent => Remote root directory: /home/jenkins
1. If it's a new master, Add credentials (Kind: SSH Username with private key, Username: jenkins, private key: <same one as before, ask around for it>)
1. Reduce executors to 1

# Running tooltester:
1. Check .tooltester/config to see if the 'server-url' needs to be changed
1. Check .tooltester/config to see if the runners specified in it are correct.  An example config that runs all runners is:
```
runner = cromwell cwltool cwl-runner
```
1. Check .tooltester/config to see if dockstore-version needs to be changed
1. Modify the [cwltoolPlaybook](src/main/resources/cwltoolPlaybook.yml) and [toilPlaybook](src/main/resources/toilPlaybook.yml) to have the right apt/pip dependencies if needed (i.e. Check the [dockstore website /onboarding](https://dockstore.org/onboarding) or [GitHub](https://github.com/dockstore/dockstore-ui2/blob/develop/src/app/loginComponents/onboarding/downloadcliclient/downloadcliclient.component.ts#L81) Step 2 Part 3 to see if changes are needed).
1. Check that the slave has enough disk space, remove /tmp and ~/workspace/* (workspace `@tmp` folders aren't removed with cleanup plugin) if needed

1. Run the ClientTest.createJenkinsTests (basically the sync commmand)
1. Run the ClientTest.enqueue (basically the enqueue command)
1. Wait until it finishes running and then run the ClientTest.report (basically the report command)

# Master Backup
1. Double check that aws is installed and has the correct credentials
1. docker cp `$ID:/var/jenkins_home .` where ID is the container ID (eventually just use a volume instead)
1. `tar cvzf jenkins_home.tar.gz jenkins_home`
1. aws s3 --endpoint-url https://object.cancercollaboratory.org:9080 cp jenkins_home.tar.gz s3://dockstore/jenkinsMaster2/jenkins_home.tar.gz


