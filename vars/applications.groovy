class applications {
  static final CASEFLOW_APPS =  ['certification', 'efolder', 'monitor']
  static final CASEFLOW_APPS_WITH_DB =  ['certification', 'efolder']
  static final UTILITY_APPS =  ['sentry']
  static final DEPLOY_TYPE = [
                               certification: 'deploys',
                               efolder: 'deploys',
                               monitor: 'blueGreens',
                               sentry: "blueGreens"
                             ]
}