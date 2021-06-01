package gov.va.appeals.devops

class Caseflow {
static final CASEFLOW_APPS =  ['certification', 'efolder', 'monitor']
static final CASEFLOW_APPS_WITH_DB =  ['certification', 'efolder']
static final DEPLOY_TYPE = [
                           certification: 'deploys',
                           efolder: 'deploys',
                           monitor: 'blueGreens'
                           ]
}
