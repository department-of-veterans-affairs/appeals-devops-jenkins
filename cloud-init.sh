#!/bin/bash

set -e

# Important Defaults
aws_default_region="us-gov-west-1"
debug=true
jenkins_url="appealsjenkins.ds.va.gov"
tz="America/New_York"

# Update and Install Jenkins
sudo yum -y update
sudo yum -y install java-1.8.0 java-1.8.0-openjdk-devel git jq
sudo yum -y remove java-1.7.0-openjdk
sudo wget -O /etc/yum.repos.d/jenkins.repo http://pkg.jenkins-ci.org/redhat/jenkins.repo
sudo rpm --import http://pkg.jenkins-ci.org/redhat/jenkins-ci.org.key
# install jenkins
sudo yum -y install jenkins
# configure timezone
sudo sed -i "s#-Djava.awt.headless=true#-Djava.awt.headless=true -Duser.timezone=$tz#" /etc/sysconfig/jenkins

# configure awscli
sudo mkdir -p ~/.aws/
sudo cat >~/.aws/config <<EOL
[default]
region = $aws_default_region
output = json
EOL

git clone https://github.com/enriquemanuel/jenkins-as-code.git /tmp/jac/
sudo cp /tmp/jac/* /var/lib/jenkins/

# get github oauth secret for config
client_id=$(aws ssm get-parameters --with-decryption --names "/jenkins/github_client_id" | jq -r  ".Parameters[0].Value")

client_secret=$(aws ssm get-parameters --with-decryption --names "/jenkins/github_client_secret" | jq -r  ".Parameters[0].Value")

github_org_name=$(aws ssm get-parameters --with-decryption --names "/jenkins/github_org_name" | jq -r  ".Parameters[0].Value")

github_admins=$(aws ssm get-parameters --with-decryption --names "/jenkins/github_admins
" | jq -r  ".Parameters[0].Value")

admin_email=$(aws ssm get-parameters --with-decryption --names "/jenkins/admin_email
" | jq -r  ".Parameters[0].Value")

# not needed later but for now lets try it
if [[ $debug == "true" ]]; then
  jenkins_url=$(curl http://169.254.169.254/latest/meta-data/public-ipv4):8080
fi

# configure the
sudo sed -i "s#<client_id>#$client_id#" /var/lib/jenkins/jenkins.yaml
sudo sed -i "s#<client_secret>#$client_secret#" /var/lib/jenkins/jenkins.yaml
sudo sed -i "s#<ip>#http://$jenkins_url/#" /var/lib/jenkins/jenkins.yaml
sudo sed -i "s#<admins>#$github_admins#" /var/lib/jenkins/jenkins.yaml
sudo sed -i "s#<org_name>#$github_org_name#" /var/lib/jenkins/jenkins.yaml
sudo sed -i "s#<admin_email>#$admin_email#" /var/lib/jenkins/jenkins.yaml
sudo sed -i "s#<aws_region>#$aws_default_region#" /var/lib/jenkins/jenkins.yaml

chown jenkins:jenkins /var/lib/jenkins/install_jenkins_plugin.sh
chmod 700 /var/lib/jenkins/install_jenkins_plugin.sh
chmod 700 /var/lib/jenkins/jenkins.yaml
chmod 700 /var/lib/jenkins/plugins.txt

# install plugins
for plugin in `cat /var/lib/jenkins/plugins.txt`; do
  /var/lib/jenkins/install_jenkins_plugin.sh $plugin;
done

sudo service jenkins start
sudo chkconfig --add jenkins
