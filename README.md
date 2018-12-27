# jenkins-as-code
Configure an intranet application that authenticates with Github Oauth and gets those secrets from SSM

## How this works?
The idea behind this is to provide an entrypoint to configure jenkins inside a VPC in AWS that is internal, but we need a repeatable configuration, so we can install the latest jenkins regularly.
This configuration will be deployed from a lambda and using SSM Execution.

## What do you need?
You need to create an EC2 with SSM in the role and then you can execute the `cloud-init.sh` in the commands section.

## How will it be executed?
- The first part of this, will update the EC2 OS correctly
- We will install Java 1.8 and Jar and some dependencies
- We then remove Java 1.7
- We then install Jenkins with its repo
- We configure aws cli
- Get the cliend id and secret from SSM 
- Update the [Jenkins Configuration as Code Plugin](https://github.com/jenkinsci/configuration-as-code-plugin) file  `jenkins.yaml`
- Install the plugins before starting since we need the Jenkins Configuration as Code Plugin to be installed
- Jenkins Configuration as Code Plugin will read the `JENKINS_HOME/jenkins.yaml` and execute it
- We have a jenkins configured :)

## What else is needed
We need to clone this repo, to get the files in the right directory accordingly
