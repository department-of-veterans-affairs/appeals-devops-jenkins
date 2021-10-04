import jenkins.model.Jenkins
import jenkins.security.s2m.AdminWhitelistRule
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage
import jenkins.AgentProtocol

// Disable deprecated JNLP protocols
def protocols = AgentProtocol.all()
protocols.each { protocol ->
  if (protocol.name && (protocol.name.contains('JNLP-connect') || protocol.name.contains('JNLP2-connect'))) {
    protocols.remove(protocol)
    jnlpChanged = true
    println "Jenkins deprecated protocol ${protocol.name} has been disabled."
  }
}

// Jenkins is noisy about this, even though it's not relevant to our situation
Jenkins.instance.getInjector()
  .getInstance(AdminWhitelistRule)
  .setMasterKillSwitch(false)

ScriptApproval.get().preapprove(
  new File('/var/lib/jenkins/jobs/seed-job/workspace/seedJob.groovy').getText('UTF-8'),
  GroovyLanguage.get()
)
