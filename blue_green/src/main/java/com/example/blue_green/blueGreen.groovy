#!/usr/bin/env groovy
// Can probably remove the line below
package com.example.blue_green;
import groovy.json.JsonSlurper
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
def String terragrunt_working_dir = '/Users/bskeen/repository/appeals-terraform/live/uat/revproxy-caseflow-replica'

def Map asg_desired_values = [
	'max_size': 2,
	'min_size': 1,
	'desired_capacity': 1
]


public def tg_apply(terragrunt_working_dir) {
	logger.info('Running tg_apply()')
	def apply_sout = new StringBuilder(), apply_serr = new StringBuilder()
	def proc_apply = "terragrunt apply -auto-approve --terragrunt-working-dir ${terragrunt_working_dir}".execute() 
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
	return [blue, green, outputs]
}

public def deploy_green(terragrunt_working_dir) {
	logger.info('Running deploy_green()')
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)
	logger.info("DEPLOYING ${green}")
	
	File tfvars = new File("${terragrunt_working_dir}/terraform.tfvars")
	if (tfvars.canRead()) {
		tfvars.delete()
	}
	tfvars.append "blue_weight_a = ${outputs.blue_weight_a}\n"
	tfvars.append "blue_weight_b = ${outputs.blue_weight_b}\n"
	tfvars.append "green_weight_a = ${outputs.green_weight_a}\n"
	tfvars.append "green_weight_b = ${outputs.green_weight_b}\n"
	
	if (green.compareTo('a').equals(0)) {
	tfvars.append "a_max_size = ${outputs.asg_configs.max_size}\n"
	tfvars.append "a_min_size = ${outputs.asg_configs.min_size}\n"
	tfvars.append "a_desired_capacity = ${outputs.asg_configs.desired_capacity}\n"
	tfvars.append "b_max_size = ${outputs.b_max_size}\n"
	tfvars.append "b_min_size = ${outputs.b_min_size}\n"
	tfvars.append "b_desired_capacity = ${outputs.b_desired_capacity}\n"
	}
	
	if (green.compareTo('b').equals(0)) {
	tfvars.append "a_max_size = ${outputs.a_max_size}\n" // these are actual outputs of ASG
	tfvars.append "a_min_size = ${outputs.a_min_size}\n"
	tfvars.append "a_desired_capacity = ${outputs.a_desired_capacity}\n"
	tfvars.append "b_max_size = ${outputs.asg_configs[1].max_size}\n" // these are desired outputs of ASG
	tfvars.append "b_min_size = ${outputs.asg_configs[1].min_size}\n"
	tfvars.append "b_desired_capacity = ${outputs.asg_configs[1].desired_capacity}\n"
	}
	String fileContents = new File("${terragrunt_working_dir}/terraform.tfvars").getText('UTF-8')
	println fileContents
	tg_apply(terragrunt_working_dir)
	tfvars.delete()
}

public def weight_shift(terragrunt_working_dir) {
	logger.info('Running weight_shift()')
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)
	
	Integer blue_weight_a = outputs.blue_weight_a
	Integer blue_weight_b = outputs.blue_weight_b
	
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

		File tfvars = new File("${terragrunt_working_dir}/terraform.tfvars")
		if (tfvars.canRead()) {
			tfvars.delete()
		}
		tfvars.append "attach_asg_to = \"${outputs.attach_asg_to}\"\n"
		tfvars.append "blue_weight_a = ${blue_weight_a}\n"
		tfvars.append "blue_weight_b = ${blue_weight_b}\n"
		tfvars.append "green_weight_a = ${outputs.green_weight_a}\n"
		tfvars.append "green_weight_b = ${outputs.green_weight_b}\n"
		
		tfvars.append "a_max_size = ${outputs.a_max_size}\n"
		tfvars.append "a_min_size = ${outputs.a_min_size}\n"
		tfvars.append "a_desired_capacity = ${outputs.a_desired_capacity}\n"
		tfvars.append "b_max_size = ${outputs.b_max_size}\n"
		tfvars.append "b_min_size = ${outputs.b_min_size}\n"
		tfvars.append "b_desired_capacity = ${outputs.b_desired_capacity}\n"

		tg_apply(terragrunt_working_dir) // only changes the attach_asg_to var in common
		tfvars.delete()
		sleep(10000)// sleeps for 10s
	}
}

public def custom_blue_weights(terragrunt_working_dir, blue_custom_weight_a, blue_custom_weight_b) {
	logger.info('Running custom_weights')
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)

	File tfvars = new File("${terragrunt_working_dir}/terraform.tfvars")
	if (tfvars.canRead()) {
		tfvars.delete()
	}
	tfvars.append "blue_weight_a = ${blue_custom_weight_a}\n"
	tfvars.append "blue_weight_b = ${blue_custom_weight_b}\n"
	tfvars.append "green_weight_a = ${outputs.green_weight_a}\n"
	tfvars.append "green_weight_b = ${outputs.green_weight_b}\n"
	tg_apply(terragrunt_working_dir) // only changes the attach_asg_to var in common
	tfvars.delete()
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
	File tfvars = new File("${terragrunt_working_dir}/terraform.tfvars")
	if (tfvars.canRead()) {
		tfvars.delete()
	}
	
	tfvars.append "blue_weight_a = ${outputs.blue_weight_a}\n"
	tfvars.append "blue_weight_b = ${outputs.blue_weight_b}\n"
	tfvars.append "green_weight_a = ${outputs.green_weight_a}\n"
	tfvars.append "green_weight_b = ${outputs.green_weight_b}\n"
	
	if (old_blue.compareTo('a').equals(0)) {
	tfvars.append "green_weight_a = 100\n"
	tfvars.append "green_weight_b = 0\n"
	
	tfvars.append "a_max_size = 0\n"
	tfvars.append "a_min_size = 0\n"
	tfvars.append "a_desired_capacity = 0\n"
	tfvars.append "b_max_size = ${outputs.b_max_size}\n"
	tfvars.append "b_min_size = ${outputs.b_min_size}\n"
	tfvars.append "b_desired_capacity = ${outputs.b_desired_capacity}\n"
	}
	
	if (old_blue.compareTo('b').equals(0)) {
	tfvars.append "green_weight_a = 0\n"
	tfvars.append "green_weight_b = 100\n"
	
	tfvars.append "a_max_size = ${outputs.a_max_size}\n" 
	tfvars.append "a_min_size = ${outputs.a_min_size}\n"
	tfvars.append "a_desired_capacity = ${outputs.a_desired_capacity}\n"
	tfvars.append "b_max_size = 0\n" 
	tfvars.append "b_min_size = 0\n"
	tfvars.append "b_desired_capacity = 0\n"
	}
	
	tg_apply(terragrunt_working_dir)
	tfvars.delete()
}

public def destroy_green(terragrunt_working_dir) {
	logger.info('Running deploy_green()')
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)
	logger.info("DEPLOYING ${green}")
	
	File tfvars = new File("${terragrunt_working_dir}/terraform.tfvars")
	if (tfvars.canRead()) {
		tfvars.delete()
	}
	tfvars.append "blue_weight_a = ${blue_weight_a}\n"
	tfvars.append "blue_weight_b = ${blue_weight_b}\n"
	tfvars.append "green_weight_a = ${outputs.green_weight_a}\n"
	tfvars.append "green_weight_b = ${outputs.green_weight_b}\n"
	
	if (green.compareTo('a').equals(0)) {
	tfvars.append "a_max_size = 0\n"
	tfvars.append "a_min_size = 0\n"
	tfvars.append "a_desired_capacity = 0\n"
	tfvars.append "b_max_size = ${outputs.b_max_size}\n"
	tfvars.append "b_min_size = ${outputs.b_min_size}\n"
	tfvars.append "b_desired_capacity = ${outputs.b_desired_capacity}\n"
	}
	
	if (green.compareTo('b').equals(0)) {
	tfvars.append "a_max_size = ${outputs.a_max_size}\n" 
	tfvars.append "a_min_size = ${outputs.a_min_size}\n"
	tfvars.append "a_desired_capacity = ${outputs.a_desired_capacity}\n"
	tfvars.append "b_max_size = 0\n" 
	tfvars.append "b_min_size = 0\n"
	tfvars.append "b_desired_capacity = 0\n"
	}
	
	tg_apply(terragrunt_working_dir)
	tfvars.delete()
}

// Treat here and down as main()
// Jenkins pipeline would pass around vars in / out instead of this file 
logger.info("Starting...")
//change_attach_asg_to(terragrunt_working_dir)
deploy_green(terragrunt_working_dir)
// def blue_custom_weight_a = 100 
// def blue_custom_weight_b = 0 
//custom_blue_weights(terragrunt_working_dir, blue_custom_weight_a, blue_custom_weight_b) 
// run_tests() // TODO: figure this out. Probably a script that runs - discuss it with team
// TODO: add define custom min, max, desired for blue, green 
//weight_shift(terragrunt_working_dir)
//destroy_old_blue(terragrunt_working_dir)
//update_green(terragrunt_working_dir)
// TODO: Make all of this work with -var, -var, -var instead of tfvars file. That will take precedence over everything else
// probably build a func to keep adding to a string of -vars
// TODO: Get this working locally 
// TODO: wire up actual A or B logic for asg_configs and auto_scaling_groups instead of relying on list index
