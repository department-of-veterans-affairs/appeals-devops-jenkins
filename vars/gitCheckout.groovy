def call(Map stageParams) {
  branch = stageParams.branch ? stageParams.branch : 'main'
  checkout([
        $class: 'GitSCM',
        branches: [[ name:  branch ]],
        userRemoteConfigs: [[ credentialsId: 'va-bot', url: stageParams.url ]],
        extensions: [[ $class: 'CleanCheckout' ]]
    ])
}
