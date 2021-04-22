#!/usr/bin/env groovy
// Can probably remove the line below
// package com.example.blue_green;

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

// TODO tomorrow: Test this shit and make sure it works when A=100 and when B=100
// it should return the current blue deployment
public def get_blue(String terragrunt_working_dir) {	
	println "Running get_blue()"
	def sout = new StringBuilder(), serr = new StringBuilder()
	"terragrunt init --terragrunt-working-dir ${terragrunt_working_dir}".execute()
	def proc_a = "terragrunt output blue_weight_a --terragrunt-working-dir ${terragrunt_working_dir}".execute()
	proc_a.consumeProcessOutput(sout, serr)
	proc_a.waitForOrKill(10000)
	def String blue_weight_a = removeBlankSpace(sout) 
	if (blue_weight_a.equals('100')) {
		def String blue = 'a'
	  return blue	
	}
	else {
		def String blue = 'b'
		return blue
	}
}

public def change_attach_asg_to(blue, terragrunt_working_dir) {
  println "Running change_attach_asg_to()"
  if (blue.equals('a')) {
    attach_asg_to = 'b'    
  }
  if (blue.equals('b')) {
    attach_asg_to = 'a'   
  }
  File tfvars = new File("${terragrunt_working_dir}/terraform.tfvars")
  tfvars.append "attach_asg_to = \"${attach_asg_to}\"\n"
  tfvars.append "blue_weight_a = 100\n"
  tfvars.append "blue_weight_b = 0\n"
  tfvars.append "green_weight_a = 0\n"
  tfvars.append "green_weight_b = 100\n"
  def sout = new StringBuilder(), serr = new StringBuilder()
  "terragrunt init --terragrunt-working-dir ${terragrunt_working_dir}".execute()
  def proc = "terragrunt apply -auto-approve --terragrunt-working-dir ${terragrunt_working_dir}".execute() 
  proc.consumeProcessOutput(sout, serr) 
  proc.waitForOrKill(9000000)
  println sout
  println serr
  tfvars.delete()
}

// Treat here and down as main()
// Jenkins pipeline would pass around vars in / out instead of this file 
println "Starting..."
def String blue = get_blue(terragrunt_working_dir)
println "Blue infrastrucure is ${blue}"
change_attach_asg_to(blue, terragrunt_working_dir)




//String[] ENVtoArray() { ENV.collect { k, v -> "$k=$v" } }
//
//"bash -c set".execute(ENVtoArray(), null).text 


