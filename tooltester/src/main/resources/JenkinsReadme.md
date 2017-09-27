Last tested with ansible 2.2.1.0
# Master Setup:
1.  Install the Jenkins pipeline suite
2.  Install all plugins filtered with "blue ocean"
3.  Create a pipeline called PipelineTest
4.  Copy the contents of resources/PipelineTest.groovy into the Pipeline Script textbox
5.  Check the checkbox:  "This project is parameterized"
6.  Create the String Parameters mentioned in the constructParameterMap(...) function
7.  Install ansible
    sudo apt-get install software-properties-common
    sudo apt-add-repository ppa:ansible/ansible
    sudo apt-get update
    sudo apt-get install -y ansible
8. Add to /etc/ansible/hosts with the following:
    [local]
    localhost
9.  Get the jenkinsPlaybook.yml from src/main/java/io.dockstore.tooltester/resources
10.  Add jenkins to the docker group
    sudo usermod -aG docker jenkins
11.  Execute the playbook
    ansible-playbook jenkinsPlaybook.yml
12.  Log in as jenkins
    sudo -u jenkins -i
13. Create an ssh keypair for jenkins
    ssh-keygen -t rsa
14. .ssh/id_rsa.pub is your public key
15. Check and see if dockstore command docker command is working correctly.


# Slave Setup:
1. Install ansible
    sudo apt-get install software-properties-common
    sudo apt-add-repository ppa:ansible/ansible
    sudo apt-get update
    sudo apt-get install -y ansible
2. Add to /etc/ansible/hosts with the following:
    [local]
    localhost
3. Download jenkinsPlaybook.yml
4. Add the jenkins user
    sudo useradd -m jenkins
5. Make a .ssh directory
    sudo -u jenkins mkdir /home/jenkins/.ssh
6. Create and edit the authorized_keys file
    sudo -u jenkins vim /home/jenkins/.ssh/authorized_keys
7. Execute the playbook
    ansible-playbook jenkinsPlaybook.yml
8. Give add jenkins to docker group
    sudo usermod -aG docker jenkins
9. Put the master's public key into the authorized_keys file of jenkins
10. Check and see if dockstore command and docker command is working correctly.
