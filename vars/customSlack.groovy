#!/usr/bin/env groovy

def call(Map stageParams) {
    buildResult = stageParams.buildResult

    message = stageParams.message ? stageParams.message : """${buildResult}
    Job: ${env.JOB_NAME}
    Build Numer: ${env.BUILD_NUMBER}
    ${currentBuild.getAbsoluteUrl()}""".stripMargin()

    success_channel = stageParams.channel
    failure_channel = stageParams.failure_channel ? stageParams.failure_channel : "appeals-devops"

    try{
        if ( buildResult == "SUCCESS" ) {
            slackSend   color: "good",
                        message: message,
                        channel: success_channel,
                        token: env.SLACK_TOKEN
        }
        else if( buildResult == "FAILURE" ) {
            slackSend   color: "danger",
                        message: message,
                        channel: failure_channel,
                        token: env.SLACK_TOKEN
        }
        else if( buildResult == "UNSTABLE" ) {
            slackSend   color: "warning",
                        message: message,
                        channel: failure_channel,
                        token: env.SLACK_TOKEN
        }
        else {
            slackSend   color: "danger",
                        message: message,
                        channel: failure_channel,
                        token: env.SLACK_TOKEN

        }
    }
    catch(err) {
        println "Failed to notify Slack: ${err}"
    }
}
