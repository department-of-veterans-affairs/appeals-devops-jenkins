#!/usr/bin/env groovy

/**
 * Send Deployment Information to New Relic. This will get the New Relic
 * credentials and then send information on deployments to applications that
 * are tracked by New Relic.
 *
 * @param  appName The name of the application.
 * @param  environment The environment of the deployment.
 * @param  commitHash The hash of the applications, last commit before the
 *                    deployment.
 * @param  cause Either the person or automation that drove the deployment.
*/
def call(Map config) {

    // create a deployment event in New Relic so we can audit our deploy history.
    // for instance, you can see production Caseflow deploys here:
    // https://rpm.newrelic.com/accounts/1788458/applications/80608544/deployments.
    println(config.appName)
    println(config.environment)
    println(commitHash)
    println(cause)
    NEW_RELIC_APP_IDS = [
      caseflow: [
        prod: 80608544,
        preprod: 81409034,
        uat: 80617951,
      ],
      'caseflow-efolder':  [
        prod: 81190810,
        preprod: 81465643,
        uat: 81495155,
      ]
    ]

    newRelicAppId = NEW_RELIC_APP_IDS[config.appName]?[config.environment]
    if (newRelicAppId) {
      jsonPayload = JsonOutput.toJson([
        deployment: [
          revision: config.commitHash,
          user: config.cause
        ]
      ])

      node {
        withCredentials([
          [
            // API token to post data to New Relic
            $class: 'StringBinding',
            credentialsId : 'NEW_RELIC_API_KEY',
            variable: 'NEW_RELIC_API_KEY',
          ],
        ]) {
        // We call `set +x` below to prevent Jenkins from logging our API key.
        sh """
          #!/bin/bash -e
          set +x

          curl -X POST 'https://api.newrelic.com/v2/applications/${newRelicAppId}/deployments.json' \
              -H "X-Api-Key:${env.NEW_RELIC_API_KEY}" -i \
              -H 'Content-Type: application/json' \
              -d '${jsonPayload}'
        """
        }
      }
    } else {
      print "New Relic deployment tracking is not enabled for ${config.appName} on ${config.environment}."
    }
}
