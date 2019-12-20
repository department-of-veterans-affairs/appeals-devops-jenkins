#!/usr/bin/env groovy

def call(Map stageParams) {
    buildResult = stageParams.buildResult
    
    slack_message = stageParams.message ? stageParams.message : """**${buildResult}**
    **Job:** ${env.JOB_NAME}
    **Build Numer:** ${env.BUILD_NUMBER}
    ${currentBuild.getAbsoluteUrl()}
    """.stripMargin()

    url = ${currentBuild.getAbsoluteUrl()}

    success_channel = stageParams.channel
    failure_channel = stageParams.failure_channel ? failure_channel: "appeals-devops"
    slack_color = "good"
    
    try{
        if ( buildResult == "SUCCESS" ) {
            slack_color = "good",
            slack_channel = success_channel

        }
        else if( buildResult == "FAILURE" ) {
            slack_color = "danger",
            slack_channel = failure_channel
        }
        else if( buildResult == "UNSTABLE" ) {
            slack_color ="warning",
            slack_channel = failure_channel
        }
        else {
            slack_color = "danger",
            slack_channel = failure_channel

        }


    slackSend(channel: slack_channel , color: slack_color, message: slack_message)

    }
    catch(err) {
        println "Failed to notify Slack: ${err}"
    }
}
