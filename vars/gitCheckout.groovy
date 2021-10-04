def call(Map stageParams) {
  branch = stageParams.branch ? stageParams.branch : 'master'
  checkout([
        $class: 'GitSCM',
        branches: [[ name:  branch ]],
        userRemoteConfigs: [[ url: stageParams.url ]],
        userRemoteConfigs: [[ credentialsId: 'va-bot', url: stageParams.url ]],
        extensions: [[ $class: 'CleanCheckout' ]]
    ])
}
