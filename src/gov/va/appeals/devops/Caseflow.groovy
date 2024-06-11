package gov.va.appeals.devops

/**
* Static class for Appeals applications deployment configurations
*/
class Caseflow {

  static final CASEFLOW_APPS =  ['certification', 'efolder', 'monitor']
  static final CASEFLOW_APPS_WITH_DB =  ['certification', 'efolder']
  static final CASEFLOW_APPS_WITH_DB_MIGRATE =  ['certification', 'efolder', 'kafka']  
  static final UTILITY_APPS =  ['sentry', 'logstash', 'caseflow-db-read', 'kafka']
  static final DEPLOY_TYPE = [
                              certification: 'blueGreens',
                              efolder: 'blueGreens',
                              monitor: 'blueGreens',
                              sentry: 'blueGreens',
                              logstash: 'blueGreens',
                              kafka: 'blueGreens',
                              "caseflow-db-read": 'blueGreens'
                           ]
  static final SCALE_DOWN = [
                            certification: [
                                           maxSize: 3,
                                           minSize: 1,
                                           desiredCapacity: 2
                                           ],
                            efolder:       [
                                           maxSize: 9,
                                           minSize: 6,
                                           desiredCapacity: 6
                                           ]
                          ]

}
