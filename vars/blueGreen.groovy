#!/usr/bin/env groovy
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.util.logging.Logger

logger = Logger.getLogger('')

/**
 * STAGE1 deploy_green
 * get_blue()
 * change attach_asg_to var, apply 
 * deploy green() // wait for green to respond with 200s
  
 * STAGE2 smoke_testing
 * run_tests(jenkins var for running the tests)
 
 * STAGE3 transition
 * weight_shift() // this is done via the weights in grunt with 200s
 * destroy_old_blue() 
 * next_deploy_prep() // change green values to correct green values and apply
*/
// TODO: remove this when getting pushed into master. This is only for local dev
// make this into a map value 1 = terragrunt_working_dir
// value 2 = test location
//def String terragrunt_working_dir = '/Users/bskeen/repository/appeals-terraform/live/uat/revproxy-caseflow-replica'
//
//def Map asg_desired_values = [
//	'max_size': 3,
//	'min_size': 3,
//	'desired_capacity':3 
//]


public def tg_apply(terragrunt_working_dir, tg_args) {
	logger.info('Running tg_apply()')
	def apply_sout = new StringBuilder(), apply_serr = new StringBuilder()
	
	def proc_apply = "terragrunt apply -auto-approve --terragrunt-working-dir ${terragrunt_working_dir} ${tg_args}".execute() 
	proc_apply.consumeProcessOutput(apply_sout, apply_serr) 
	proc_apply.waitForOrKill(9000000)
	logger.info("PROC_APPLY SERR = ${apply_serr}") // This is info. It's all the terragrunt vomit `running command: terraform init [...]` 
	logger.info("PROC_APPLY SOUT = ${apply_sout}")
}

public def get_blue_green(terragrunt_working_dir) {	
	logger.info("Running get_blue_green()")
	def jsonSlurper = new JsonSlurper()
	def init_sout = new StringBuilder(), init_serr = new StringBuilder()
	def proc_init =	"terragrunt init --terragrunt-source-update --terragrunt-working-dir ${terragrunt_working_dir}".execute()
	proc_init.consumeProcessOutput(init_sout, init_serr) 
	proc_init.waitForOrKill(9000000)
	
	def sout = new StringBuilder(), serr = new StringBuilder()
	def proc = "terragrunt output -json --terragrunt-source-update --terragrunt-working-dir ${terragrunt_working_dir}".execute() 
	proc.consumeProcessOutput(sout, serr) 
	proc.waitForOrKill(9000000)
	def object = jsonSlurper.parseText(sout.toString()) 
	def Map outputs = [
	'blue_weight_a':object.blue_weight_a.value, 
	'blue_weight_b':object.blue_weight_b.value,
	'green_weight_a':object.green_weight_a.value,
	'green_weight_b':object.green_weight_b.value,
	// TODO: potentially an if here. A might not always be 0 - might be 1 at times
	// current values of A and B asgs
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
		logger.error("ERROR: Neither blue_weight_a or blue_weight_b is greater than 50")
		System.exit(1)	
	}

	if (outputs.green_weight_a.equals(100)) {
		green = 'a'
	}
	else if (outputs.green_weight_b.equals(100)) {
		green = 'b'
	} 
	else {
		logger.error("ERROR: Neither green_weight_a or green_weight_b is set to 100")
		System.exit(1)
	}
	logger.info("OUTPUTS = ${outputs}")
	println blue
	println green
	return [blue, green, outputs]
}

def call(Map config) {
	println "TESTTESTTEST"
	terragrunt_working_dir = config.tg_work_dir
	asg_desired_values = config.desired_values
//def call(String terragrunt_working_dir, Map asg_desired_values) {
//public def deploy_green(terragrunt_working_dir, asg_desired_values) {
	println 'Running deploy_green()'
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)
	println "DEPLOYING ${green}"
	if (green.compareTo('a').equals(0)) {
		def Map new_a_asg_configs = [	
			'suffix':'a',
			'max_size': asg_desired_values.max_size,
			'min_size': asg_desired_values.min_size,
			'desired_capacity': asg_desired_values.desired_capacity
		]
		
		def Map new_b_asg_configs = [
			'suffix':'b',
			'max_size':	outputs.b_max_size,
			'min_size': outputs.b_min_size,
			'desired_capacity': outputs.b_desired_capacity
		]
		new_asg_configs = [new_a_asg_configs, new_b_asg_configs]
	}
	
	if (green.compareTo('b').equals(0)) {
		def Map new_a_asg_configs = [	
			'suffix':'a',
			'max_size': outputs.a_max_size,
			'min_size': outputs.a_min_size,
			'desired_capacity':outputs.a_desired_capacity
		]
		
		def Map new_b_asg_configs = [
			'suffix':'b',
			'max_size': asg_desired_values.max_size,
			'min_size': asg_desired_values.min_size,
			'desired_capacity': asg_desired_values.desired_capacity
		]
		new_asg_configs = [new_a_asg_configs, new_b_asg_configs]
	}
	
	tg_args = tg_args_builder(outputs, new_asg_configs)
	tg_apply(terragrunt_working_dir, tg_args)
}

public def tg_args_builder(outputs, new_asg_configs) {
	outputs.remove("asg_configs")
    outputs.remove("a_max_size")
    outputs.remove("a_min_size")
    outputs.remove("a_desired_capacity")
    outputs.remove("b_max_size")
    outputs.remove("b_min_size")
    outputs.remove("b_desired_capacity")

	def String tg_args = ""
	for (item in outputs) {
		tg_args = tg_args + "-var=${item.key}=${item.value} "
	}
	println tg_args
	def String asg_configs = ""
	for (item in new_asg_configs) {
		def builder = new JsonBuilder()
		builder(item)
		asg_configs = asg_configs + builder.toString() + ","
	}
	println asg_configs
	tg_args = tg_args + "-var=asg_configs=[${asg_configs}]"
	return tg_args
}


public def weight_shift(terragrunt_working_dir) {
	logger.info('Running weight_shift()')
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)
	
	Integer blue_weight_a = outputs.blue_weight_a
	Integer blue_weight_b = outputs.blue_weight_b
	
	def Map new_a_asg_configs = [	
		'suffix':'a',
		'max_size': outputs.a_max_size,
		'min_size': outputs.a_min_size,
		'desired_capacity':outputs.a_desired_capacity
	]
	
	def Map new_b_asg_configs = [
		'suffix':'b',
		'max_size': outputs.b_max_size,
		'min_size': outputs.b_min_size,
		'desired_capacity': outputs.b_desired_capacity
	]
	
	new_asg_configs = [new_a_asg_configs, new_b_asg_configs]
	
	while (blue_weight_a != 100 || blue_weight_b != 100) {
		// blue weight shift starts here
		if (blue.compareTo('a').equals(0)) {
			blue_weight_a = blue_weight_a-10
			blue_weight_b = blue_weight_b+10
		}	
		
		else if (blue.compareTo('b').equals(0)) {
			blue_weight_a = blue_weight_a+10
			blue_weight_b = blue_weight_b-10
		}	
		
		if (blue_weight_a == 110 || blue_weight_b == 110) {		
        	break	
        }
		
		outputs["blue_weight_a"] = blue_weight_a
		outputs["blue_weight_b"] = blue_weight_b
		
		tg_args = tg_args_builder(outputs, new_asg_configs)	
		tg_apply(terragrunt_working_dir, tg_args) 
		sleep(10000)// sleeps for 10s
	}
}

public def custom_blue_weights(terragrunt_working_dir, blue_custom_weight_a, blue_custom_weight_b) {
	logger.info('Running custom_blue_weights()')
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)
	
	outputs["blue_weight_a"] = blue_custom_weight_a
	outputs["blue_weight_b"] = blue_custom_weight_b
	
	def Map new_a_asg_configs = [	
		'suffix':'a',
		'max_size': outputs.a_max_size,
		'min_size': outputs.a_min_size,
		'desired_capacity':outputs.a_desired_capacity
	]
	
	def Map new_b_asg_configs = [
		'suffix':'b',
		'max_size': outputs.b_max_size,
		'min_size': outputs.b_min_size,
		'desired_capacity': outputs.b_desired_capacity
	]
	
	new_asg_configs = [new_a_asg_configs, new_b_asg_configs]
	tg_args = tg_args_builder(outputs, new_asg_configs)	
	tg_apply(terragrunt_working_dir, tg_args) // only changes the attach_asg_to var in common
}

public def destroy_old_blue(terragrunt_working_dir) {
	logger.info("Running destroy_old_blue()")
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)
	if (blue.compareTo('a').equals(0)) {
		old_blue = 'b'
	}
	else if (blue.compareTo('b').equals(0)) {
    	old_blue = 'a'
    }
	logger.info("DESTROYING OLD BLUE ${old_blue}")
	
	if (old_blue.compareTo('a').equals(0)) {
		outputs["green_weight_a"] = 100
		outputs["green_weight_b"] = 0
		def Map new_a_asg_configs = [	
			'suffix':'a',
			'max_size': 0,
			'min_size': 0,
			'desired_capacity': 0
		]
		
		def Map new_b_asg_configs = [
			'suffix':'b',
			'max_size': outputs.b_max_size,
			'min_size': outputs.b_min_size,
			'desired_capacity': outputs.b_desired_capacity
		]
		new_asg_configs = [new_a_asg_configs, new_b_asg_configs]	
	}
	
	if (old_blue.compareTo('b').equals(0)) {
		outputs["green_weight_a"] = 0 
		outputs["green_weight_b"] = 100
		def Map new_a_asg_configs = [	
			'suffix':'a',
			'max_size': 0, 
			'min_size': 0,
			'desired_capacity': 0
		]
		
		def Map new_b_asg_configs = [
			'suffix':'b',
			'max_size': outputs.b_max_size,
			'min_size': outputs.b_min_size,
			'desired_capacity': outputs.b_desired_capacity
		]
		new_asg_configs = [new_a_asg_configs, new_b_asg_configs]	
	}
	
	tg_args = tg_args_builder(outputs, new_asg_configs)	
	tg_apply(terragrunt_working_dir, tg_args)
}

public def destroy_green(terragrunt_working_dir) {
	logger.info('Running deploy_green()')
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)
	logger.info("DESTROYING ${green}")
	
	outputs["blue_weight_a"] = outputs.blue_weight_a
    outputs["blue_weight_b"] = outputs.blue_weight_b
	outputs["green_weight_a"] = outputs.green_weight_a
    outputs["green_weight_b"] = outputs.green_weight_b

	if (green.compareTo('a').equals(0)) {
		def Map new_a_asg_configs = [	
			'suffix':'a',
			'max_size': 0, 
			'min_size': 0,
			'desired_capacity': 0
		]
		
		def Map new_b_asg_configs = [
			'suffix':'b',
			'max_size': outputs.b_max_size,
			'min_size': outputs.b_min_size,
			'desired_capacity': outputs.b_desired_capacity
		]
		new_asg_configs = [new_a_asg_configs, new_b_asg_configs]
		tg_args = tg_args_builder(outputs, new_asg_configs)	
	}
	
	if (green.compareTo('b').equals(0)) {
		def Map new_a_asg_configs = [	
			'suffix':'a',
			'max_size': outputs.a_max_size,
			'min_size': outputs.a_min_size,
			'desired_capacity': outputs.a_desired_capacity
		]
		
		def Map new_b_asg_configs = [
			'suffix':'b',
			'max_size': 0, 
			'min_size': 0, 
			'desired_capacity': 0 
		]	
		new_asg_configs = [new_a_asg_configs, new_b_asg_configs]		
	}
	tg_args = tg_args_builder(outputs, new_asg_configs)	
	tg_apply(terragrunt_working_dir, tg_args)
}

def print_local_dir() {
	logger.info('RUNNING PRINT_LOCAL_DIR')
	def apply_sout = new StringBuilder(), apply_serr = new StringBuilder()
	
	def proc_apply = "pwd".execute() 
	proc_apply.consumeProcessOutput(apply_sout, apply_serr) 
	proc_apply.waitForOrKill(9000000)
	logger.info("PROC_APPLY SERR = ${apply_serr}") // This is info. It's all the terragrunt vomit `running command: terraform init [...]` 
	logger.info("PROC_APPLY SOUT = ${apply_sout}")
}



//print_local_dir()
// Treat here and down as main()
//logger.info("Starting...")
//deploy_green(terragrunt_working_dir, asg_desired_values)
//def blue_custom_weight_a = 40 
//def blue_custom_weight_b = 60 
//custom_blue_weights(terragrunt_working_dir, blue_custom_weight_a, blue_custom_weight_b) 
// run_tests() // TODO: figure this out. Probably a script that runs - discuss it with team
//weight_shift(terragrunt_working_dir)
//destroy_old_blue(terragrunt_working_dir)
