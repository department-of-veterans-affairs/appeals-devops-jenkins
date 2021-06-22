#!/usr/bin/env groovy
import static gov.va.appeals.devops.Environments.PROD_ENVS
import static gov.va.appeals.devops.Caseflow.CASEFLOW_APPS
import static gov.va.appeals.devops.Jenkins.DEPLOY_JOBS

def call(Map stageParams) {

    buildResult = stageParams.buildResult
    appName = stageParams.appName
    environment = stageParams.environment
    messageType = stageParams.messageType
    jobType = env.JOB_NAME.split('/')[0]
    amiHash = stageParams.amiHash != null ? "Git hash: `${stageParams.amiHash}`" : ''
    stageParamsMessage = stageParams.message != '' ? "\n" + """```${stageParams.message}```""" : ''
    message = ''

    if (messageType == "START") {
        message = """Start Jenkins pipeline job `${env.JOB_NAME}` for `${appName}` to environment `${environment}`. ${stageParamsMessage}"""
        if (environment in PROD_ENVS
           && appName in CASEFLOW_APPS
           && jobType in DEPLOY_JOBS) {

             message = "@here " + message
        }
    }
    else if (messageType == "FINISH") {
        message =  """Finished Jenkins pipeline job `${env.JOB_NAME}` for `${appName}` to environment `${environment}`.
                        |${amiHash} ${stageParamsMessage}""".stripMargin()
        if (jobType in DEPLOY_JOBS) {
            message = "*Deployment Successful*\n" + message
        }
    }
    else if (messageType == "FAILURE") {
        message = """@here Failed Jenkins pipeline for `${env.JOB_NAME}` on application `${appName ?: ''}` to environment `${environment}`!
                        |Reason: `${stageParams.error}`
                        |${currentBuild.getAbsoluteUrl()}console ${stageParamsMessage}""".stripMargin()
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
