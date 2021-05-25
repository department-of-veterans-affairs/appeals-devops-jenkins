#!/usr/bin/env groovy

def call(Map stageParams) {
    final NONPROD_ENVS =  ['uat', 'preprod']
    final PROD_ENVS =  ['prod']
    final CASEFLOW_APPS =  ['certification', 'efolder', 'monitor']
    final CASEFLOW_DB_APPS =  ['certification', 'efolder']
    final DEPLOY_JOBS = ['blueGreens', 'deploys']

    buildResult = stageParams.buildResult
    appName = stageParams.appName
    environment = stageParams.environment
    messageType = stageParams.messageType
    jobType = env.JOB_NAME.split('/')[0]
    amiHash = stageParams.amiHash != null ? "Git hash: `${stageParams.amiHash}`" : ''
    message = ''

    if (messageType == "START") {
        message = """Start Jenkins pipelne job `${env.JOB_NAME}` for `${appName}` to environment `${environment}`.
                  ```${stageParams.message}```
        """
        if (environment in PROD_ENVS
           && appName in CASEFLOW_APPS
           && jobType in DEPLOY_JOBS) {

             message = "@here " + message
        }
    }
    else if (messageType == "FINISH") {
        message =  """Finished Jenkins pipeline job `${env.JOB_NAME}` for `${appName}` to environment `${environment}`.
                        |${amiHash}
                        ```${stageParams.message}```""".stripMargin()
        if (jobType in DEPLOY_JOBS) {
            message = "Deployment Successful -- \n" + message
        }
    }
    else if (messageType == "FAILURE") {
        message = """@here Failed Jenkins pipeline for `${env.JOB_NAME}` on application `${appName ?: ''}` to environment `${environment}`!
                        |Reason: `${stageParams.error}`
                        |${currentBuild.getAbsoluteUrl()}console
                        ```${stageParams.message}```""".stripMargin()
    }
    else {
        message = stageParams.message ? stageParams.message : """${buildResult}
                  |Job: ${env.JOB_NAME}
                  |Build Numer: ${env.BUILD_NUMBER}
                  |${currentBuild.getAbsoluteUrl()}""".stripMargin()
    }

    success_channel = stageParams.channel
    failure_channel = stageParams.failure_channel ? stageParams.failure_channel : "appeals-devops-alerts"

    try{
        if ( buildResult == "SUCCESS" ) {
            slackSend   color: "good",
                        message: message,
                        channel: success_channel
        }
        else if( buildResult == "FAILURE" ) {
            slackSend   color: "danger",
                        message: message,
                        channel: failure_channel
        }
        else if( buildResult == "UNSTABLE" ) {
            slackSend   color: "warning",
                        message: message,
                        channel: failure_channel
        }
        else {
            slackSend   color: "danger",
                        message: message,
                        channel: failure_channel
        }
    }
    catch(err) {
        println "Failed to notify Slack: ${err}"
    }
}
