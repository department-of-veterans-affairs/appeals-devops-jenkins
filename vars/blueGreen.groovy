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
	println "TERRA COMMAND = ${terraInfo.terraPath}/terragrunt/terragrunt${terraInfo.tgruntVersion} apply -auto-approve --terragrunt-working-dir ${terragruntWorkingDir} ${tgArgs} --terragrunt-tfpath ${terraInfo.terraPath}/terraform/terraform${terraInfo.tformVersion}"	
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
	'blue_weight_a':object.blue_weight_a.value, 
	'blue_weight_b':object.blue_weight_b.value,
	'green_weight_a':object.green_weight_a.value,
	'green_weight_b':object.green_weight_b.value,
	
	'a_max_size':object.auto_scaling_groups.value[0].max_size,
	'a_min_size':object.auto_scaling_groups.value[0].min_size,
	'a_desired_capacity':object.auto_scaling_groups.value[0].desired_capacity,
	'b_max_size':object.auto_scaling_groups.value[1].max_size,
	'b_min_size':object.auto_scaling_groups.value[1].min_size,
	'b_desired_capacity':object.auto_scaling_groups.value[1].desired_capacity,
	'asg_configs': object.asg_configs.value,
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

public def deployGreen(terragruntWorkingDir, asgDesiredValues, terraInfo, extraArgs) {
	println 'Running deployGreen()'
	(blue, green, outputs) = getBlueGreen(terragruntWorkingDir, terraInfo)
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
			'max_size':	outputs.b_max_size,
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
	tgApply(terragruntWorkingDir, tgArgs, terraInfo)
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
	//println tgArgs
	def String asgConfigs = ""
	for (item in newAsgConfigs) {
		def builder = new JsonBuilder()
		builder(item)
		asgConfigs = asgConfigs + builder.toString() + ","
	}
	//println asgConfigs
	tgArgs = tgArgs + "-var=asg_configs=[${asgConfigs}]"
	if (extraArgs) {
    	tgArgs = tgArgs + " " + extraArgs
    }
	return tgArgs
}

public def weightShift(terragruntWorkingDir, terraInfo, extraArgs) {
	println 'Running weightShift()'
	(blue, green, outputs) = getBlueGreen(terragruntWorkingDir, terraInfo)
	
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
		
		outputs["blue_weight_a"] = blueWeightA
		outputs["blue_weight_b"] = blueWeightB
		
		tgArgs = tgArgsBuilder(outputs, newAsgConfigs, extraArgs)	
		tgApply(terragruntWorkingDir, tgArgs, terraInfo) 
		sleep(10)// sleeps for 10s
	}
}

public def customBlueWeights(terragruntWorkingDir, blueCustomWeightA, blueCustomWeightB, terraInfo) {
	println 'Running customBlueWeights()'
	(blue, green, outputs) = getBlueGreen(terragruntWorkingDir, terraInfo)
	
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
	tgApply(terragruntWorkingDir, tgArgs, terraInfo) 
}

public def destroyOldBlue(terragruntWorkingDir, terraInfo, extraArgs) {
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
	tgApply(terragruntWorkingDir, tgArgs, terraInfo)
}

public def destroy_green(terragruntWorkingDir, terraInfo) {
	println 'Running destroyGreen()'
	(blue, green, outputs) = getBlueGreen(terragruntWorkingDir, terraInfo)
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
	tgApply(terragruntWorkingDir, tgArgs, terraInfo)
}

