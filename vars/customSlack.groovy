#!/usr/bin/env groovy

def call(Map stageParams) {
    buildResult = stageParams.buildResult
    
    message = stageParams.message ? stageParams.message : """**${buildResult}**
    **Job**: ${env.JOB_NAME}
    **Build Numer**: ${env.BUILD_NUMBER}
    ${currentBuild.getAbsoluteUrl()}"""

    success_channel = stageParams.channel
    failure_channel = stageParams.failure_channel ? failure_channel: "appeals-devops"
    
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
