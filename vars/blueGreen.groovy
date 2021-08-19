#!/usr/bin/env groovy
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

/**
 * STAGE1 deployGreen

 * STAGE2 run_tests_on_green 

 * STAGE3 if green_test_result = true: weight shift

 * STAGE4 destroyOldBlue

*/

public def tgApply(terragruntWorkingDir, tgArgs) {
  println 'Running tgApply()'
  TERRAGRUNT_COMMAND = "terragrunt apply -auto-approve --terragrunt-working-dir ${terragruntWorkingDir} ${tgArgs}"
  println TERRAGRUNT_COMMAND
  timeout(time: 15, unit: 'MINUTES') {
     sh TERRAGRUNT_COMMAND
  }

}

public def sumAsgs(autoScalingGroups, targetGroupName) {
  def Map indexLookup = [
    a: 0,
    b: 1
  ]
  println(autoScalingGroups)
  def maxSize = 0
  def minSize = 0
  def desiredCapacity = 0

  def startIndex = indexLookup[targetGroupName]
  def endIndex = autoScalingGroups.size()
  def stepAmount = indexLookup.size() 
  // Returns a subset of either the 'a' or the 'b' asgs
  startIndex.step endIndex, stepAmount, {
    println(autoScalingGroups[it])
    println(autoScalingGroups[it].max_size)
    maxSize += autoScalingGroups[it].max_size as Integer
    minSize += autoScalingGroups[it].min_size as Integer
    desiredCapacity += autoScalingGroups[it].desired_capacity as Integer
  }
  return [ maxSize, minSize, desiredCapacity ]
}

public def getBlueGreen(terragruntWorkingDir) {
  println "Running getBlueGreen()"
  timeout(time: 5, unit: 'MINUTES') {
    tgInitStdout = sh(returnStdout: true, script: "set +x\n terragrunt init --terragrunt-source-update --terragrunt-working-dir ${terragruntWorkingDir}")
  }
  echo tgInitStdout
  timeout(time: 5, unit: 'MINUTES') {
    tgOutputStdout = sh(returnStdout: true, script: "set +x\n terragrunt output -json --terragrunt-source-update --terragrunt-working-dir ${terragruntWorkingDir}")
  }
  echo tgOutputStdout


  def jsonSlurper = new JsonSlurper()
  def tgOutput = jsonSlurper.parseText(tgOutputStdout)
  def autoScalingGroups = tgOutput.auto_scaling_groups.value

  println(tgOutput)
  println(autoScalingGroups)
  def (aMaxSize, aMinSize, aDesiredCapacity) = sumAsgs(autoScalingGroups, 'a')
  def (bMaxSize, bMinSize, bDesiredCapacity) = sumAsgs(autoScalingGroups, 'b')

  def Map outputs = [
  'blue_weight_a':tgOutput.blue_weight_a.value, 
  'blue_weight_b':tgOutput.blue_weight_b.value,
  'green_weight_a':tgOutput.green_weight_a.value,
  'green_weight_b':tgOutput.green_weight_b.value,
  'a_max_size':aMaxSize,
  'a_min_size':aMinSize,
  'a_desired_capacity':aDesiredCapacity,
  'b_max_size':bMaxSize,
  'b_min_size':bMinSize,
  'b_desired_capacity':bDesiredCapacity,
  'asg_configs': tgOutput.asg_configs.value,
  ]
  if (outputs.blue_weight_a >= 50) {
    blue = 'a'
  }
  else if (outputs.blue_weight_b >= 50) {
    blue = 'b'
  } 
  else {
    println "ERROR: Neither blue_weight_a or blue_weight_b is greater than 50"
    System.exit(1)  
  }

  if (outputs.green_weight_a.equals(100)) {
    green = 'a'
  }
  else if (outputs.green_weight_b.equals(100)) {
    green = 'b'
  } 
  else {
    println "ERROR: Neither green_weight_a or green_weight_b is set to 100"
    System.exit(1)
  }
  println "OUTPUTS = ${outputs}"
  println "BLUE = ${blue}"
  println "GREEN = ${green}"
  return [blue, green, outputs]
}

public def deployGreen(terragruntWorkingDir, asgDesiredValues, extraArgs) {
  println 'Running deployGreen()'
  (blue, green, outputs) = getBlueGreen(terragruntWorkingDir)
  println "DEPLOYING ${green}"
  if (green.compareTo('a').equals(0)) {
    def Map newAAsgConfigs = [
      'suffix':'a',
      'max_size': asgDesiredValues.maxSize,
      'min_size': asgDesiredValues.minSize,
      'desired_capacity': asgDesiredValues.desiredCapacity
    ]

    def Map newBAsgConfigs = [
      'suffix':'b',
      'max_size': outputs.b_max_size,
      'min_size': outputs.b_min_size,
      'desired_capacity': outputs.b_desired_capacity
    ]
    newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]
  }

  if (green.compareTo('b').equals(0)) {
    def Map newAAsgConfigs = [
      'suffix':'a',
      'max_size': outputs.a_max_size,
      'min_size': outputs.a_min_size,
      'desired_capacity':outputs.a_desired_capacity
    ]

    def Map newBAsgConfigs = [
      'suffix':'b',
      'max_size': asgDesiredValues.maxSize,
      'min_size': asgDesiredValues.minSize,
      'desired_capacity': asgDesiredValues.desiredCapacity
    ]
    newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]
  }

  tgArgs = tgArgsBuilder(outputs, newAsgConfigs, extraArgs)
  tgApply(terragruntWorkingDir, tgArgs)
}

public def tgArgsBuilder(outputs, newAsgConfigs, extraArgs) {
  outputs.remove("asg_configs")
  outputs.remove("a_max_size")
  outputs.remove("a_min_size")
  outputs.remove("a_desired_capacity")
  outputs.remove("b_max_size")
  outputs.remove("b_min_size")
  outputs.remove("b_desired_capacity")
  def String tgArgs = ""
  for (item in outputs) {
    tgArgs = tgArgs + "-var=${item.key}=${item.value} "
  }

  def String asgConfigs = ""
  for (item in newAsgConfigs) {
    def builder = new JsonBuilder()
    builder(item)
    asgConfigs = asgConfigs + builder.toString() + ","
  }
  // The single quotes are required or bash will try to expand asg_configs
  tgArgs = tgArgs + "'-var=asg_configs=[${asgConfigs}]'"
  if (extraArgs) {
      tgArgs = tgArgs + " " + extraArgs
    }
  return tgArgs
}

public def weightShift(terragruntWorkingDir, weightShift, extraArgs) {
  println 'Running weightShift()'
  (blue, green, outputs) = getBlueGreen(terragruntWorkingDir)

  Integer blueWeightA = outputs.blue_weight_a
  Integer blueWeightB = outputs.blue_weight_b

  def Map newAAsgConfigs = [  
    'suffix':'a',
    'max_size': outputs.a_max_size,
    'min_size': outputs.a_min_size,
    'desired_capacity':outputs.a_desired_capacity
  ]

  def Map newBAsgConfigs = [
    'suffix':'b',
    'max_size': outputs.b_max_size,
    'min_size': outputs.b_min_size,
    'desired_capacity': outputs.b_desired_capacity
  ]

  newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]

  while (blueWeightA != 100 || blueWeightB != 100) {
    // blue weight shift starts here
    if (blue.compareTo('a').equals(0)) {
      blueWeightA = blueWeightA - weightShift
      blueWeightB = blueWeightB + weightShift
    } 

    else if (blue.compareTo('b').equals(0)) {
      blueWeightA = blueWeightA + weightShift
      blueWeightB = blueWeightB - weightShift
    } 

    if (blueWeightA == (100 +  weightShift) || blueWeightB == (100 + weightShift)) {
          break 
        }

    outputs["blue_weight_a"] = blueWeightA
    outputs["blue_weight_b"] = blueWeightB

    tgArgs = tgArgsBuilder(outputs, newAsgConfigs, extraArgs) 
    tgApply(terragruntWorkingDir, tgArgs)
  }
}

public def customBlueWeights(terragruntWorkingDir, blueCustomWeightA, blueCustomWeightB) {
  println 'Running customBlueWeights()'
  (blue, green, outputs) = getBlueGreen(terragruntWorkingDir)

  outputs["blue_weight_a"] = blueCustomWeightA
  outputs["blue_weight_b"] = blueCustomWeightB

  def Map newAAsgConfigs = [
    'suffix':'a',
    'max_size': outputs.a_max_size,
    'min_size': outputs.a_min_size,
    'desired_capacity':outputs.a_desired_capacity
  ]

  def Map newBAsgConfigs = [
    'suffix':'b',
    'max_size': outputs.b_max_size,
    'min_size': outputs.b_min_size,
    'desired_capacity': outputs.b_desired_capacity
  ]

  newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]
  tgArgs = tgArgsBuilder(outputs, newAsgConfigs, extraArgs) 
  tgApply(terragruntWorkingDir, tgArgs)
}

public def destroyOldBlue(terragruntWorkingDir, extraArgs) {
  println "Running destroyOldBlue()"
  (blue, green, outputs) = getBlueGreen(terragruntWorkingDir)
  if (blue.compareTo('a').equals(0)) {
    old_blue = 'b'
  }
  else if (blue.compareTo('b').equals(0)) {
      old_blue = 'a'
    }
  println "DESTROYING OLD BLUE ${old_blue}"

  if (old_blue.compareTo('a').equals(0)) {
    outputs["green_weight_a"] = 100
    outputs["green_weight_b"] = 0
    def Map newAAsgConfigs = [  
      'suffix':'a',
      'max_size': 0,
      'min_size': 0,
      'desired_capacity': 0
    ]

    def Map newBAsgConfigs = [
      'suffix':'b',
      'max_size': outputs.b_max_size,
      'min_size': outputs.b_min_size,
      'desired_capacity': outputs.b_desired_capacity
    ]
    newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]  
  }

  if (old_blue.compareTo('b').equals(0)) {
    outputs["green_weight_a"] = 0 
    outputs["green_weight_b"] = 100
    def Map newAAsgConfigs = [
      'suffix':'a',
      'max_size': outputs.a_max_size, 
      'min_size': outputs.a_min_size,
      'desired_capacity': outputs.a_desired_capacity
    ]

    def Map newBAsgConfigs = [
      'suffix':'b',
      'max_size': 0,
      'min_size': 0,
      'desired_capacity': 0 
    ]
    newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]  
  }

  tgArgs = tgArgsBuilder(outputs, newAsgConfigs, extraArgs) 
  tgApply(terragruntWorkingDir, tgArgs)
}

public def destroy_green(terragruntWorkingDir) {
  println 'Running destroyGreen()'
  (blue, green, outputs) = getBlueGreen(terragruntWorkingDir)
  println "DESTROYING ${green}"

  outputs["blue_weight_a"] = outputs.blue_weight_a
        outputs["blue_weight_b"] = outputs.blue_weight_b
  outputs["green_weight_a"] = outputs.green_weight_a
        outputs["green_weight_b"] = outputs.green_weight_b

  if (green.compareTo('a').equals(0)) {
    def Map newAAsgConfigs = [
      'suffix':'a',
      'max_size': 0, 
      'min_size': 0,
      'desired_capacity': 0
    ]

    def Map newBAsgConfigs = [
      'suffix':'b',
      'max_size': outputs.b_max_size,
      'min_size': outputs.b_min_size,
      'desired_capacity': outputs.b_desired_capacity
    ]
    newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]
    tgArgs = tgArgsBuilder(outputs, newAsgConfigs, extraArgs) 
  }

  if (green.compareTo('b').equals(0)) {
    def Map newAAsgConfigs = [
      'suffix':'a',
      'max_size': outputs.a_max_size,
      'min_size': outputs.a_min_size,
      'desired_capacity': outputs.a_desired_capacity
    ]

    def Map newBAsgConfigs = [
      'suffix':'b',
      'max_size': 0, 
      'min_size': 0, 
      'desired_capacity': 0 
    ] 
    newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]    
  }
  tgArgs = tgArgsBuilder(outputs, newAsgConfigs, extraArgs) 
  tgApply(terragruntWorkingDir, tgArgs)
}

