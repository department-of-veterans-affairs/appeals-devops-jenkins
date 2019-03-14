import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.impl.*
import com.michelin.cio.hudson.plugins.rolestrategy.*
import hudson.model.Cause
import hudson.plugins.s3.S3BucketPublisher
import hudson.plugins.s3.S3BucketPublisher.DescriptorImpl
import hudson.plugins.s3.S3Profile
import hudson.security.*
import hudson.security.AuthorizationStrategy
import hudson.security.SecurityRealm
import hudson.util.Secret
import jenkins.model.Jenkins
import jenkins.security.s2m.AdminWhitelistRule
import org.jenkinsci.plugins.GithubAuthorizationStrategy
import org.jenkinsci.plugins.GithubSecurityRealm
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;

import jenkins.AgentProtocol
import hudson.model.RootAction

Jenkins.instance.setNoUsageStatistics(true)
Jenkins.instance.getSetupWizard()?.completeSetup()
Jenkins.instance.getDescriptor("jenkins.CLI").get().setEnabled(false)

// Disable deprecated JNLP protocols
def protocols = AgentProtocol.all()
protocols.each { protocol ->
  if(protocol.name && (protocol.name.contains("JNLP-connect") || protocol.name.contains("JNLP2-connect"))) {
    protocols.remove(protocol)
    jnlpChanged = true
    println "Jenkins deprecated protocol ${protocol.name} has been disabled."
  }
}

// Jenkins is noisy about this, even though it's not relevant to our situation
Jenkins.instance.getInjector()
  .getInstance(AdminWhitelistRule.class)
  .setMasterKillSwitch(false)


ScriptApproval.get().preapprove(
  new File('/var/lib/jenkins/workspace/seed-job/seedJob.groovy').getText('UTF-8'),
  GroovyLanguage.get()
)
