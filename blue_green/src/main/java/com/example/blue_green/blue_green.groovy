#!/usr/bin/env groovy
// Can probably remove the line below
// package com.example.blue_green;
import groovy.json.JsonSlurper
//TODO: Figure out logging. Right now there are a lot of print statements
/**
 * Hello world!
 *
 *
 * public class App 
 * {
 *     public static void main( String[] args )
 *     {
 *         System.out.println( "Hello World!" );
 *     }
 * }

 * All of these in one file (skip step 2 for the moment)
 * STAGE0 monitoring (paralell to all other stages) (in the future)
 *        if any failure ABORT

 * STAGE1 deploy_green
 * get_outputs()
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
def String terragrunt_working_dir = '/Users/bskeen/repository/appeals-terraform/live/uat/revproxy-caseflow-replica/common'

public String removeBlankSpace(StringBuilder sb) {
  println "Running removeBlankSpace()"
  int j = 0;
  for(int i = 0; i < sb.length(); i++) {
    if (!Character.isWhitespace(sb.charAt(i))) {
       sb.setCharAt(j++, sb.charAt(i));
    }
  }
  return sb.delete(j, sb.length());
}

public def get_blue(terragrunt_working_dir) {	
	println "Running get_blue()"
	if (outputs.get('blue_weight_a').equals(100)) {
		def String blue = 'a'
		return blue	
	}
	if (outputs.get('blue_weight_b').equals(100)) {
		def String blue = 'b'
		return blue
	} 
	else {
		println "ERROR: Neither blue_weight_a or blue_weight_b is set to 100"
		System.exit(1)
	}
}

public def change_attach_asg_to(outputs, terragrunt_working_dir) {
	println "Running change_attach_asg_to()"
	if (outputs.get('blue_weight_a').compareTo(100).equals(0)) {
		attach_asg_to = 'b'
		println "ATTACHING TO ${attach_asg_to}"
	}
	else if (outputs.get('blue_weight_b').compareTo(100).equals(0)) {
		attach_asg_to = 'a'
		println "ATTACHING TO ${attach_asg_to}"
	}
	else {
		println "ERROR: Neither blue_weight_a or blue_weight_b is set to 100"
		System.exit(1)
	}
	File tfvars = new File("${terragrunt_working_dir}/terraform.tfvars")
	tfvars.append "attach_asg_to = \"${attach_asg_to}\"\n"
	tfvars.append "blue_weight_a = ${outputs.get('blue_weight_a')}\n"
	tfvars.append "blue_weight_b = ${outputs.get('blue_weight_b')}\n"
	tfvars.append "green_weight_a = ${outputs.get('green_weight_a')}\n"
	tfvars.append "green_weight_b = ${outputs.get('green_weight_b')}\n"
	def sout = new StringBuilder(), serr = new StringBuilder()
	"terragrunt init --terragrunt-working-dir ${terragrunt_working_dir}".execute()
	def proc = "terragrunt apply -auto-approve --terragrunt-working-dir ${terragrunt_working_dir}".execute() 
	proc.consumeProcessOutput(sout, serr) 
	proc.waitForOrKill(9000000)
	println sout
	println tfvars.getText('UTF-8')
	tfvars.delete()
}

public Map get_outputs(terragrunt_working_dir) {
  	def jsonSlurper = new JsonSlurper()
  	"terragrunt init --terragrunt-working-dir ${terragrunt_working_dir}".execute()
  	def sout = new StringBuilder(), serr = new StringBuilder()
  	def proc = "terragrunt output -json --terragrunt-working-dir ${terragrunt_working_dir}".execute() 
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
	return outputs
}

// Treat here and down as main()
// Jenkins pipeline would pass around vars in / out instead of this file 
println "Starting..."
def Map outputs = get_outputs(terragrunt_working_dir)
println outputs 
//def String blue = get_blue(outputs, terragrunt_working_dir) // This is no longer NEEDED but might be nice to have 
//println "Blue infrastrucure is ${blue}"
change_attach_asg_to(outputs, terragrunt_working_dir)

