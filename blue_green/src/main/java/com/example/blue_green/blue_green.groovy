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

// TODO tomorrow: Test this shit and make sure it works when A=100 and when B=100
// it should return the current blue deployment
static def get_blue(String terragrunt_working_dir) {	
	def sout = new StringBuilder(), serr = new StringBuilder()
	"terragrunt init --terragrunt-working-dir ${terragrunt_working_dir}".execute()
	def proc_a = "terragrunt output blue_weight_a --terragrunt-working-dir ${terragrunt_working_dir}".execute()
	proc_a.consumeProcessOutput(sout, serr)
	proc_a.waitForOrKill(10000)
	if(sout == "100") {
		def String blue = 'a'
		return blue
	}
	else {
		def String blue = 'b'
		return blue
	}
}

// Treat here and down as main()
// Jenkins pipeline would pass around vars in / out instead of this file 
def String blue = get_blue(terragrunt_working_dir)
println blue
