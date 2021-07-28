package gov.va.appeals.devops

class Caseflow {
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
static final GEM_VERSIONS = [
                           certification: '3.1.6',
                           efolder: '2.7.8',
                           monitor: '2.7.8'
                            ]
