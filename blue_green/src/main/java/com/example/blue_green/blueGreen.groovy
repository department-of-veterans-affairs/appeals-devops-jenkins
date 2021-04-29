#!/usr/bin/env groovy
// Can probably remove the line below
package com.example.blue_green;
import groovy.json.JsonSlurper
import java.util.logging.Logger

logger = Logger.getLogger('')
logger.info('Hello from a Job DSL script!')

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

public def tg_apply(terragrunt_working_dir, infra_set) {
	logger.info('Running tg_apply()')
	def apply_sout = new StringBuilder(), apply_serr = new StringBuilder()
	def proc_apply = "terragrunt apply -auto-approve --terragrunt-working-dir ${terragrunt_working_dir}/${infra_set}".execute() 
	proc_apply.consumeProcessOutput(apply_sout, apply_serr) 
	proc_apply.waitForOrKill(9000000)
	logger.info("PROC_APPLY SERR = ${apply_serr}") // This is info. It's all the terragrunt vomit `running command: terraform init [...]` 
	logger.info("PROC_APPLY SOUT = ${apply_sout}")
}

public def tg_destroy(terragrunt_working_dir, infra_set) {
	logger.info('Running tg_destroy()')
	def apply_sout = new StringBuilder(), apply_serr = new StringBuilder()
	def proc_apply = "terragrunt destroy -auto-approve --terragrunt-working-dir ${terragrunt_working_dir}/${infra_set}".execute() 
	proc_apply.consumeProcessOutput(apply_sout, apply_serr) 
	proc_apply.waitForOrKill(9000000)
	logger.info("PROC_APPLY SERR = ${apply_serr}") // This is info. It's all the terragrunt vomit `running command: terraform init [...]` 
	logger.info("PROC_APPLY SOUT = ${apply_sout}")
}

public def get_blue_green(terragrunt_working_dir) {	
	logger.info("Running get_blue_green()")
	def String infra_set = 'common'
	def jsonSlurper = new JsonSlurper()
	def init_sout = new StringBuilder(), init_serr = new StringBuilder()
	def proc_init =	"terragrunt init --terragrunt-source-update --terragrunt-working-dir ${terragrunt_working_dir}/${infra_set}".execute()
	proc_init.consumeProcessOutput(init_sout, init_serr) 
	proc_init.waitForOrKill(9000000)
	
	def sout = new StringBuilder(), serr = new StringBuilder()
	def proc = "terragrunt output -json --terragrunt-source-update --terragrunt-working-dir ${terragrunt_working_dir}/${infra_set}".execute() 
	proc.consumeProcessOutput(sout, serr) 
	proc.waitForOrKill(9000000)
	def object = jsonSlurper.parseText(sout.toString()) 
	def Map outputs = [
	'attach_asg_to':object.get('attach_asg_to').get('value'),
	'blue_weight_a':object.get('blue_weight_a').get('value'), 
	'blue_weight_b':object.get('blue_weight_b').get('value'),
	'green_weight_a':object.get('green_weight_a').get('value'),
	'green_weight_b':object.get('green_weight_b').get('value'),
	]
	if (outputs.get('blue_weight_a') >= 50) {
		blue = 'a'
	}
	else if (outputs.get('blue_weight_b') >= 50) {
		blue = 'b'
	} 
	else {
		logger.error("ERROR: Neither blue_weight_a or blue_weight_b is greater than 50")
		System.exit(1)	
	}

	if (outputs.get('green_weight_a').equals(100)) {
		green = 'a'
	}
	else if (outputs.get('green_weight_b').equals(100)) {
		green = 'b'
	} 
	else {
		logger.error("ERROR: Neither green_weight_a or green_weight_b is set to 100")
		System.exit(1)
	}
	logger.info("OUTPUTS = ${outputs}")
	return [blue, green, outputs]
}

public def change_attach_asg_to(terragrunt_working_dir) {
	logger.info("Running change_attach_asg_to()")
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)	
	if (blue.compareTo('a').equals(0)) {
		attach_asg_to = 'b'
		logger.info("ATTACHING TO ${attach_asg_to}")
	}
	else if (blue.compareTo('b').equals(0)) {
		attach_asg_to = 'a'
		logger.info("ATTACHING TO ${attach_asg_to}")
	}
	def String infra_set = 'common'
	File tfvars = new File("${terragrunt_working_dir}/${infra_set}/terraform.tfvars")
	if (tfvars.canRead()) {
		tfvars.delete()
	}
	tfvars.append "attach_asg_to = \"${attach_asg_to}\"\n"
	tfvars.append "blue_weight_a = ${outputs.get('blue_weight_a')}\n"
	tfvars.append "blue_weight_b = ${outputs.get('blue_weight_b')}\n"
	tfvars.append "green_weight_a = ${outputs.get('green_weight_a')}\n"
	tfvars.append "green_weight_b = ${outputs.get('green_weight_b')}\n"
	tg_apply(terragrunt_working_dir, infra_set) // only changes the attach_asg_to var in common
	tfvars.delete()
}

public def deploy_green(terragrunt_working_dir) {
	logger.info('Running deploy_green()')
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)
	logger.info("DEPLOYING ${green}")
	tg_apply(terragrunt_working_dir, green)
}

public def weight_shift(terragrunt_working_dir) {
	logger.info('Running weight_shift()')
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)
	def infra_set = 'common'
	
	Integer blue_weight_a = outputs.get('blue_weight_a')
	Integer blue_weight_b = outputs.get('blue_weight_b')
	
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

		File tfvars = new File("${terragrunt_working_dir}/${infra_set}/terraform.tfvars")
		if (tfvars.canRead()) {
			tfvars.delete()
		}
		tfvars.append "attach_asg_to = \"${outputs.get('attach_asg_to')}\"\n"
		tfvars.append "blue_weight_a = ${blue_weight_a}\n"
		tfvars.append "blue_weight_b = ${blue_weight_b}\n"
		tfvars.append "green_weight_a = ${outputs.get('green_weight_a')}\n"
		tfvars.append "green_weight_b = ${outputs.get('green_weight_b')}\n"
		tg_apply(terragrunt_working_dir, infra_set) // only changes the attach_asg_to var in common
		tfvars.delete()
		sleep(10000)// sleeps for 10s
	}
}

public def custom_blue_weights(terragrunt_working_dir, blue_custom_weight_a, blue_custom_weight_b) {
	logger.info('Running custom_weights')
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)
	String infra_set = 'common'

	File tfvars = new File("${terragrunt_working_dir}/${infra_set}/terraform.tfvars")
	if (tfvars.canRead()) {
		tfvars.delete()
	}
	tfvars.append "attach_asg_to = \"${outputs.get('attach_asg_to')}\"\n"
	tfvars.append "blue_weight_a = ${blue_custom_weight_a}\n"
	tfvars.append "blue_weight_b = ${blue_custom_weight_b}\n"
	tfvars.append "green_weight_a = ${outputs.get('green_weight_a')}\n"
	tfvars.append "green_weight_b = ${outputs.get('green_weight_b')}\n"
	tg_apply(terragrunt_working_dir, infra_set) // only changes the attach_asg_to var in common
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
	tg_destroy(terragrunt_working_dir, old_blue)
}

public def destroy_green(terragrunt_working_dir) {
	logger.info("Running destroy_green()")
	(blue, green, outputs) = get_blue_green(terragrunt_working_dir)
	if (green.compareTo('a').equals(0)) {
		green = 'a'
	}
	else if (blue.compareTo('b').equals(0)) {
    	green = 'a'
    }
	logger.info("DESTROYING OLD BLUE ${green}")
	tg_destroy(terragrunt_working_dir, green)
}

public def update_green(terragrunt_working_dir) {
  logger.info("Running update_green()")
  def String infra_set = 'common'
  (blue, green, outputs) = get_blue_green(terragrunt_working_dir)
  if (blue.compareTo('a').equals(0)) {
  	green_weight_a = 0 
    green_weight_b = 100
  }
  else if (blue.compareTo('b').equals(0)) {
    green_weight_a = 100 
    green_weight_b = 0
  }
  File tfvars = new File("${terragrunt_working_dir}/${infra_set}/terraform.tfvars")
  if (tfvars.canRead()) {
  	tfvars.delete()
  }
  tfvars.append "attach_asg_to = \"${outputs.get('attach_asg_to')}\"\n"
  tfvars.append "blue_weight_a = ${outputs.get('blue_weight_a')}\n"
  tfvars.append "blue_weight_b = ${outputs.get('blue_weight_b')}\n"
  tfvars.append "green_weight_a = ${green_weight_a}\n"
  tfvars.append "green_weight_b = ${green_weight_b}\n"
  tg_apply(terragrunt_working_dir, infra_set)
  tfvars.delete()
}


// Treat here and down as main()
// Jenkins pipeline would pass around vars in / out instead of this file 
logger.info("Starting...")
change_attach_asg_to(terragrunt_working_dir)
deploy_green(terragrunt_working_dir)
// def blue_custom_weight_a = 100 
// def blue_custom_weight_b = 0 
// custom_blue_weights(terragrunt_working_dir, blue_custom_weight_a, blue_custom_weight_b) 
// run_tests() // TODO: figure this out. Probably a script that runs - discuss it with team
weight_shift(terragrunt_working_dir)
destroy_old_blue(terragrunt_working_dir)
update_green(terragrunt_working_dir)
