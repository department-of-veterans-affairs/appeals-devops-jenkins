@Grab('org.yaml:snakeyaml:1.17')

import groovy.transform.Field
import org.yaml.snakeyaml.Yaml

@Field workspace = new File("/var/lib/jenkins/workspace/seed-job")
@Field repoPath = "/var/lib/jenkins/workspace/seed-job/appeals-deployment"
def deploymentBranch

def checkout() {
  def jenkinsRepo = "https://" + GIT_CREDENTIAL + "@github.com/department-of-veterans-affairs/appeals-deployment.git"
  try {
    deploymentBranch = "${DEPLOYMENT_DEV_BRANCH}"
  }
  catch(Exception ex){
    deploymentBranch = "master"
  }
  println("cloning deployment repo with branch ${deploymentBranch}...")
  def gitProc = ["git", "clone", "-b", deploymentBranch, jenkinsRepo].execute(null, workspace)
  def out = new StringBuffer()
  def err = new StringBuffer()
  gitProc.consumeProcessOutput(out, err)
  gitProc.waitForOrKill(10*60*1000) // 10 minutes
  println(out)
  println(err)
  def exitCode = gitProc.exitValue()
  if (exitCode != 0) {
    println("git exited with code: ${exitCode}")
    throw new Exception("git process exit code was ${exitCode}")
  }
}

def createFolderFromYaml(String folderName, File yamlFile) {
  Yaml parser = new Yaml()
  def metadata = parser.load(yamlFile.text)

  println("Creating Folder... $folderName")

  folder(folderName) {
    displayName(metadata['displayName'] ?: "")
    description(metadata['description'] ?: "")
  }
}

def createFolder(String folderName, String folderFullName) {
  folder(folderFullName) {
    displayName(folderName)
  }
}

def createJobFromGroovy(String folderName, File groovyFile) {
  println("Creating Job... $folderName")

  GroovyShell shell = new GroovyShell(this.binding)
  def script = shell.parse(groovyFile)

  def arguments = [:]
  arguments['folderName'] = folderName
  arguments['jenkins'] = this
  script.invokeMethod("createJob", arguments)
}

def scanFolder(File folder, String parentFolderName) {
  def folderName = parentFolderName + "/" + folder.getName()
  def isJob = false

  File jobFile = new File(folder.absolutePath + "/job.groovy")
  File folderFile = new File(folder.absolutePath + "/folder.yml")

  if(jobFile.exists()) {
    return createJobFromGroovy(folderName, jobFile)
  }

  if(folderFile.exists()) {
    createFolderFromYaml(folderName, folderFile)
  }
  else {
    createFolder(folder.getName(), folderName)
  }

  for (file in folder.listFiles()) {
    if(file.isDirectory()) {
      scanFolder(file, folderName)
    }
  }
}

def scanRootFolder(File folder) {
  for (file in folder.listFiles()) {
    if(file.isDirectory()) {
      scanFolder(file, "")
    }
  }
}

// clean up the appeals deployment repo to make sure we have a clean start
["rm", "-rf", repoPath].execute().waitFor()

checkout()
def jobsFolder = new File(repoPath + "/jenkins-jobs")
scanRootFolder(jobsFolder)
