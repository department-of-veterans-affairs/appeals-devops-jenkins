package gov.va.appeals.devops

class Caseflow {
static final CASEFLOW_APPS =  ['certification', 'efolder', 'monitor']
static final CASEFLOW_APPS_WITH_DB =  ['certification', 'efolder']
static final UTILITY_APPS =  ['sentry']
static final DEPLOY_TYPE = [
                           certification: 'deploys',
                           efolder: 'blueGreens',
                           monitor: 'blueGreens',
                           sentry: "blueGreens"
                           ]
static final SCALE_DOWN = [
                            certification: [
                                           maxSize: 3,
                                           minSize: 1,
                                           desiredCapacity: 2
                                           ],
                            // efolder:       [
                            //                maxSize: 9
                            //                minSize: 6
                            //                desiredCapacity: 6
                            //                ]
                            efolder:       [ // temp settings for testing
                                           maxSize: 2,
                                           minSize: 2,
                                           desiredCapacity: 2
                                           ]
                          ]
}
