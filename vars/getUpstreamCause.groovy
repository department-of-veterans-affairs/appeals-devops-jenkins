def call(Map config ) {
  def triggerd_from_upstream = currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause)
  def cause = triggerd_from_upstream ? config.originalCause : currentBuild.rawBuild.getCauses()[0].getShortDescription()
  triggerd_from_upstream = null
  return cause
}
