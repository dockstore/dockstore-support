<<<<<<< HEAD
# Master Setup:
=======
Master Setup:
>>>>>>> d7e8c79... Feature/jenkins example (#5)
1.  Install the Jenkins pipeline suite
2.  Create a pipeline called PipelineTest
3.  Copy the contents of resources/PipelineTest.groovy into the Pipeline Script textbox
4.  Check the checkbox:  "This project is parameterized"
5.  Create 5 String Parameters with the names: "URL", "Tag", "DescriptorPath", "ParameterPath", "DockerfilePath"
<<<<<<< HEAD
6.  Install ansible
    sudo apt-get install software-properties-common
    sudo apt-add-repository ppa:ansible/ansible
    sudo apt-get update
    sudo apt-get install ansible
7.  Get the jenkinsPlaybook.yml from src/main/java/io.dockstore.tooltester/resources
8.  Execute the playbook
    ansible-playbook jenkinsPlaybook.yml

# Slave Setup:
1. Add the jenkins user
    sudo useradd -m jenkins
2. Make a .ssh directory
    sudo -u jenkins mkdir /home/jenkins/.ssh
3. Create and edit the authorized_keys file
    sudo -u jenkins vim /home/jenkins/.ssh/authorized_keys
4. Put the master's key into the authorized_keys file
5. Give the jenkins user permission to run all commands in the playbook
    sudo visudo and then add jenkins ALL= NOPASSWD: ALL
6. Install ansible
    sudo apt-get install software-properties-common
    sudo apt-add-repository ppa:ansible/ansible
    sudo apt-get update
    sudo apt-get install -y ansible
6. Add to /etc/ansible/hosts with the following:
    [local]
    localhost
7. Install python-pip
    sudo apt-get install python-pip
6. Download jenkinsPlaybook.yml
7. Execute the playbook
    ansible-playbook jenkinsPlaybook.yml
=======
6.  sudo apt-get install ansible
7.  Get the jenkinsPlaybook.yml from src/main/java/io.dockstore.tooltester/resources
7.  ansible-playbook jenkinsPlaybook.yml

Slave Setup:
1. sudo apt-get update
2. sudo apt-get upgrade
3. sudo apt-get install default-jre
4. sudo useradd -m jenkins
5. sudo -u jenkins mkdir /home/jenkins/.ssh
6. sudo -u jenkins vim /home/jenkins/.ssh/authorized_keys
7. Put the master's key into the authorized_keys file
8. sudo visudo and then add jenkins ALL= NOPASSWD: ALL
9. Downloading jenkinsPlaybook.yml
10. ansible-playbook jenkinsPlaybook.yml
>>>>>>> d7e8c79... Feature/jenkins example (#5)
