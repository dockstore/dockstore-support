Last tested with ansible 2.2.1.0

The general idea is to only have the `jenkins` user talk to each other, never using the `ubuntu` user.

Also for simplicity, always SSH into the jump server directly to get to the master and slaves.

# Master Setup:
1. Login to [Collaboratory console](https://console.cancercollaboratory.org/)
1. Launch a new instance with the following properties
    1. Flavour - c2.micro
    1. Source - Ubuntu 18.04 image
    1. Security groups - Jenkins, Default
1. SSH into jump server and from there SSH into master. Run the setupMaster.sh in master (download from this repository)
1. Log out and log back in

Follow these steps if there is a backup

---
1. Configure the aws cli using `aws configure`. Ask around for the credentials (~/.aws/credentials)
1. aws s3 --endpoint-url https://object.cancercollaboratory.org:9080 cp s3://dockstore/jenkinsMaster2/jenkins_home.tar.gz jenkins_home.tar.gz
1. Extract with `tar xvzf jenkins_home.tar.gz` and then remove the far file
1. Run the jenkins container
    `docker run --restart=always -u root -d -p 8080:8080 -p 50000:50000 -v $PWD/jenkins_home:/var/jenkins_home -v /var/run/docker.sock:/var/run/docker.sock jenkinsci/blueocean:1.13.1`
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
1. Login to [Collaboratory console](https://console.cancercollaboratory.org/)
1. Launch a new instance with the following properties
    1. Flavour - c2.large
    1. Source - Ubuntu 18.04 image
    1. Key Pair - JenkinsMaster2 (may need to ask someone on the team for this)
1. SSH into the jump server and from there SSH into the master and then to slave. Clone this repository, checkout the develop branch, and run the setupSlave.sh.
1. Configure the aws cli using `sudo -u jenkins -i` and then `aws configure`. Use the credentials in the jenkins@jenkins-master's ~/.aws/credentials
1. Configure slave on master Jenkin ({master-floating-ip}:8080):
    1. Manage Jenkins => Manage Nodes => New Node ({master-floating-ip}:8080/computer/new)
        1. Type in the name for the node and select permanent agent. Click Ok.
        1. \# of executors - 1
        1. Remote root directory - /home/jenkins
1. If it's a new master, Add credentials (Kind: SSH Username with private key, Username: jenkins, private key: <same one as before, ask around for it>)
1. If it's not a new master and there are already invalid nodes, configure and set the IP address then save.
    1. Host - {ip-address-slave}
    1. Credentials - Jenkins
    1. Host Key Verification Strategy - Non verifying Verification Strategy
1. When in doubt, look at configuration of existing nodes.

# Running tooltester:
A sample config file is shown below. This one will run tool tester with staging and dockstore-version 1.7.0-beta.6.

Ensure that these fields are filled out correctly. Note that the dockstore-version below is for the Dockstore CLI version (not webservice)

```
runner = cromwell cwltool cwl-runner

server-url = https://staging.dockstore.org/api
jenkins-server-url = http://<find-on-collab>:8080/
jenkins-username = <username>
jenkins-password = <password>
development = true
dockstore-version = 1.7.0-beta.6

```
1. Tooltester will try to find the CLI based on the dockstore-version above, ensure it's available at https://github.com/dockstore/dockstore-cli/releases/download/{{dockstore-version}}/dockstore
1. On your local machine, fill in ~/.tooltester/config with the appropriate values
    1. In particular, ensure that `server-url`, `runners`, and `dockstore-version` are properly set
1. Clone https://github.com/dockstore/dockstore-support.git. Open in Intellij: Import project -> pom.xml as project
1. In the resources directory, modify the 4 config files as needed.
1. Optional: Modify the [cwltoolPlaybook](src/main/resources/cwltoolPlaybook.yml) and [toilPlaybook](src/main/resources/toilPlaybook.yml) in the feature/playbook branch to have the right apt/pip dependencies if needed (i.e. check the [dockstore website /onboarding](https://dockstore.org/onboarding) or [GitHub](https://github.com/dockstore/dockstore-ui2/blob/develop/src/app/loginComponents/onboarding/downloadcliclient/downloadcliclient.component.ts#L81) Step 2 Part 3 to see if changes are needed).
1. IMPORTANT: Check that the slave has enough disk space, remove /tmp and /home/jenkins/workspace/* (workspace `@tmp` folders aren't removed with cleanup plugin) if needed
1. Run the ClientTest.createJenkinsTests by pressing the green run button (basically the sync commmand)
1. Run the ClientTest.enqueue by pressing the green run button (basically the enqueue command)
1. Wait until it finishes running.
1. io/dockstore/tooltester/client/cli/ReportCommand.java has a SEND_LOGS boolean.  Check that your S3 credentials work (using aws cli) if sending logs to S3. Otherwise, change the boolean to false.
1. Run the ClientTest.report (basically the report command)
    1. You can view running jobs at {master-floating-ip}:8080

# Master Backup
1. Double check that aws is installed and has the correct credentials
1. docker cp `$ID:/var/jenkins_home .` where ID is the container ID (eventually just use a volume instead)
1. `tar cvzf jenkins_home.tar.gz jenkins_home`
1. aws s3 --endpoint-url https://object.cancercollaboratory.org:9080 cp jenkins_home.tar.gz s3://dockstore/jenkinsMaster2/jenkins_home.tar.gz


