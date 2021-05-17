#!/usr/bin/env groovy
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

/**
 * STAGE1 deployGreen
  
 * STAGE2 run_tests_on_green 
 
 * STAGE3 if green_test_result = true: weight shift
 
 * STAGE4 destroyOldBlue

*/

public def tgApply(terragruntWorkingDir, tgArgs, terraInfo) {
	println 'Running tgApply()'
	
	def applySout = new StringBuilder(), applySerr = new StringBuilder()
	def procApply = "${terraInfo.terraPath}/terragrunt/terragrunt${terraInfo.tgruntVersion} apply -auto-approve --terragrunt-working-dir ${terragruntWorkingDir} ${tgArgs} --terragrunt-tfpath ${terraInfo.terraPath}/terraform/terraform${terraInfo.tformVersion}".execute() 
	procApply.consumeProcessOutput(applySout, applySerr) 
	procApply.waitForOrKill(9000000)
	println "PROC_APPLY SERR = ${applySerr}" // This is info. It's all the terragrunt vomit `running command: terraform init [...]` 
	println "PROC_APPLY SOUT = ${applySout}"
}

public def getBlueGreen(terragruntWorkingDir, terraInfo) {	
	println "Running getBlueGreen()"
	
	def jsonSlurper = new JsonSlurper()
	def initSout = new StringBuilder(), initSerr = new StringBuilder()
	def procInit =	"${terraInfo.terraPath}/terragrunt/terragrunt${terraInfo.tgruntVersion} init --terragrunt-source-update --terragrunt-working-dir ${terragruntWorkingDir} --terragrunt-tfpath ${terraInfo.terraPath}/terraform/terraform${terraInfo.tformVersion}".execute()
	procInit.consumeProcessOutput(initSout, initSerr) 
	procInit.waitForOrKill(9000000)
	
	def sout = new StringBuilder(), serr = new StringBuilder()
	def proc = "${terraInfo.terraPath}/terragrunt/terragrunt${terraInfo.tgruntVersion} output -json --terragrunt-source-update --terragrunt-working-dir ${terragruntWorkingDir} --terragrunt-tfpath ${terraInfo.terraPath}/terraform/terraform${terraInfo.tformVersion}".execute() 
	proc.consumeProcessOutput(sout, serr) 
	proc.waitForOrKill(9000000)
	def object = jsonSlurper.parseText(sout.toString()) 
	def Map outputs = [
	'blueWeightA':object.blue_weight_a.value, 
	'blueWeightB':object.blue_weight_b.value,
	'greenWeightA':object.green_weight_a.value,
	'greenWeightB':object.green_weight_b.value,
	
	'aMaxSize':object.auto_scaling_groups.value[0].maxSize,
	'aMinSize':object.auto_scaling_groups.value[0].minSize,
	'aDesiredCapacity':object.auto_scaling_groups.value[0].desiredCapacity,
	'bMaxSize':object.auto_scaling_groups.value[1].maxSize,
	'bMinSize':object.auto_scaling_groups.value[1].minSize,
	'bDesiredCapacity':object.auto_scaling_groups.value[1].desiredCapacity,
	'asgConfigs': object.asg_configs.value,
	]
	if (outputs.blueWeightA >= 50) {
		blue = 'a'
	}
	else if (outputs.blueWeightB >= 50) {
		blue = 'b'
	} 
	else {
		println "ERROR: Neither blueWeightA or blueWeightB is greater than 50"
		System.exit(1)	
	}

	if (outputs.greenWeightA.equals(100)) {
		green = 'a'
	}
	else if (outputs.greenWeightB.equals(100)) {
		green = 'b'
	} 
	else {
		println "ERROR: Neither greenWeightA or greenWeightB is set to 100"
		System.exit(1)
	}
	println "OUTPUTS = ${outputs}"
	println "BLUE = ${blue}"
	println "GREEN = ${green}"
	return [blue, green, outputs]
}

public def deployGreen(terragruntWorkingDir, asgDesiredValues, terraInfo) {
	println 'Running deployGreen()'
	(blue, green, outputs) = getBlueGreen(terragruntWorkingDir, terraInfo)
	println "DEPLOYING ${green}"
	if (green.compareTo('a').equals(0)) {
		def Map newAAsgConfigs = [	
			'suffix':'a',
			'maxSize': asgDesiredValues.maxSize,
			'minSize': asgDesiredValues.minSize,
			'desiredCapacity': asgDesiredValues.desiredCapacity
		]
		
		def Map newBAsgConfigs = [
			'suffix':'b',
			'maxSize':	outputs.bMaxSize,
			'minSize': outputs.bMinSize,
			'desiredCapacity': outputs.bDesiredCapacity
		]
		newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]
	}
	
	if (green.compareTo('b').equals(0)) {
		def Map newAAsgConfigs = [	
			'suffix':'a',
			'maxSize': outputs.aMaxSize,
			'minSize': outputs.aMinSize,
			'desiredCapacity':outputs.aDesiredCapacity
		]
		
		def Map newBAsgConfigs = [
			'suffix':'b',
			'maxSize': asgDesiredValues.maxSize,
			'minSize': asgDesiredValues.minSize,
			'desiredCapacity': asgDesiredValues.desiredCapacity
		]
		newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]
	}
	
	tgArgs = tgArgsBuilder(outputs, newAsgConfigs)
	tgApply(terragruntWorkingDir, tgArgs, terraInfo)
}

public def tgArgsBuilder(outputs, newAsgConfigs) {
	outputs.remove("asgConfigs")
    outputs.remove("aMaxSize")
    outputs.remove("aMinSize")
    outputs.remove("aDesiredCapacity")
    outputs.remove("bMaxSize")
    outputs.remove("bMinSize")
    outputs.remove("bDesiredCapacity")

	def String tgArgs = ""
	for (item in outputs) {
		tgArgs = tgArgs + "-var=${item.key}=${item.value} "
	}
	//println tgArgs
	def String asgConfigs = ""
	for (item in newAsgConfigs) {
		def builder = new JsonBuilder()
		builder(item)
		asgConfigs = asgConfigs + builder.toString() + ","
	}
	//println asgConfigs
	tgArgs = tgArgs + "-var=asgConfigs=[${asgConfigs}]"
	return tgArgs
}

public def weightShift(terragruntWorkingDir, terraInfo) {
	println 'Running weightShift()'
	(blue, green, outputs) = getBlueGreen(terragruntWorkingDir, terraInfo)
	
	Integer blueWeightA = outputs.blueWeightA
	Integer blueWeightB = outputs.blueWeightB
	
	def Map newAAsgConfigs = [	
		'suffix':'a',
		'maxSize': outputs.aMaxSize,
		'minSize': outputs.aMinSize,
		'desiredCapacity':outputs.aDesiredCapacity
	]
	
	def Map newBAsgConfigs = [
		'suffix':'b',
		'maxSize': outputs.bMaxSize,
		'minSize': outputs.bMinSize,
		'desiredCapacity': outputs.bDesiredCapacity
	]
	
	newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]
	
	while (blueWeightA != 100 || blueWeightB != 100) {
		// blue weight shift starts here
		if (blue.compareTo('a').equals(0)) {
			blueWeightA = blueWeightA-10
			blueWeightB = blueWeightB+10
		}	
		
		else if (blue.compareTo('b').equals(0)) {
			blueWeightA = blueWeightA+10
			blueWeightB = blueWeightB-10
		}	
		
		if (blueWeightA == 110 || blueWeightB == 110) {		
        	break	
        }
		
		outputs["blueWeightA"] = blueWeightA
		outputs["blueWeightB"] = blueWeightB
		
		tgArgs = tgArgsBuilder(outputs, newAsgConfigs)	
		tgApply(terragruntWorkingDir, tgArgs, terraInfo) 
		sleep(10)// sleeps for 10s
	}
}

public def customBlueWeights(terragruntWorkingDir, blueCustomWeightA, blueCustomWeightB, terraInfo) {
	println 'Running customBlueWeights()'
	(blue, green, outputs) = getBlueGreen(terragruntWorkingDir, terraInfo)
	
	outputs["blueWeightA"] = blueCustomWeightA
	outputs["blueWeightB"] = blueCustomWeightB
	
	def Map newAAsgConfigs = [	
		'suffix':'a',
		'maxSize': outputs.aMaxSize,
		'minSize': outputs.aMinSize,
		'desiredCapacity':outputs.aDesiredCapacity
	]
	
	def Map newBAsgConfigs = [
		'suffix':'b',
		'maxSize': outputs.bMaxSize,
		'minSize': outputs.bMinSize,
		'desiredCapacity': outputs.bDesiredCapacity
	]
	
	newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]
	tgArgs = tgArgsBuilder(outputs, newAsgConfigs)	
	tgApply(terragruntWorkingDir, tgArgs, terraInfo) 
}

public def destroyOldBlue(terragruntWorkingDir, terraInfo) {
	println "Running destroyOldBlue()"
	(blue, green, outputs) = getBlueGreen(terragruntWorkingDir, terraInfo)
	if (blue.compareTo('a').equals(0)) {
		old_blue = 'b'
	}
	else if (blue.compareTo('b').equals(0)) {
    	old_blue = 'a'
    }
	println "DESTROYING OLD BLUE ${old_blue}"
	
	if (old_blue.compareTo('a').equals(0)) {
		outputs["greenWeightA"] = 100
		outputs["greenWeightB"] = 0
		def Map newAAsgConfigs = [	
			'suffix':'a',
			'maxSize': 0,
			'minSize': 0,
			'desiredCapacity': 0
		]
		
		def Map newBAsgConfigs = [
			'suffix':'b',
			'maxSize': outputs.bMaxSize,
			'minSize': outputs.bMinSize,
			'desiredCapacity': outputs.bDesiredCapacity
		]
		newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]	
	}
	
	if (old_blue.compareTo('b').equals(0)) {
		outputs["greenWeightA"] = 0 
		outputs["greenWeightB"] = 100
		def Map newAAsgConfigs = [	
			'suffix':'a',
			'maxSize': outputs.aMaxSize, 
			'minSize': outputs.aMinSize,
			'desiredCapacity': outputs.aDesiredCapacity
		]
		
		def Map newBAsgConfigs = [
			'suffix':'b',
			'maxSize': 0,
			'minSize': 0,
			'desiredCapacity': 0 
		]
		newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]	
	}
	
	tgArgs = tgArgsBuilder(outputs, newAsgConfigs)	
	tgApply(terragruntWorkingDir, tgArgs, terraInfo)
}

public def destroy_green(terragruntWorkingDir, terraInfo) {
	println 'Running deployGreen()'
	(blue, green, outputs) = getBlueGreen(terragruntWorkingDir, terraInfo)
	println "DESTROYING ${green}"
	
	outputs["blueWeightA"] = outputs.blueWeightA
    outputs["blueWeightB"] = outputs.blueWeightB
	outputs["greenWeightA"] = outputs.greenWeightA
    outputs["greenWeightB"] = outputs.greenWeightB

	if (green.compareTo('a').equals(0)) {
		def Map newAAsgConfigs = [	
			'suffix':'a',
			'maxSize': 0, 
			'minSize': 0,
			'desiredCapacity': 0
		]
		
		def Map newBAsgConfigs = [
			'suffix':'b',
			'maxSize': outputs.bMaxSize,
			'minSize': outputs.bMinSize,
			'desiredCapacity': outputs.bDesiredCapacity
		]
		newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]
		tgArgs = tgArgsBuilder(outputs, newAsgConfigs)	
	}
	
	if (green.compareTo('b').equals(0)) {
		def Map newAAsgConfigs = [	
			'suffix':'a',
			'maxSize': outputs.aMaxSize,
			'minSize': outputs.aMinSize,
			'desiredCapacity': outputs.aDesiredCapacity
		]
		
		def Map newBAsgConfigs = [
			'suffix':'b',
			'maxSize': 0, 
			'minSize': 0, 
			'desiredCapacity': 0 
		]	
		newAsgConfigs = [newAAsgConfigs, newBAsgConfigs]		
	}
	tgArgs = tgArgsBuilder(outputs, newAsgConfigs)	
	tgApply(terragruntWorkingDir, tgArgs, terraInfo)
}

