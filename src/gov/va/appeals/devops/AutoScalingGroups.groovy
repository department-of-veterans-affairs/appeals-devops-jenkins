package gov.va.appeals.devops

@Grab('software.amazon.awssdk:autoscaling:2.17.33')
import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.autoscaling.model.*


//println region
//AutoScalingClient asg =  AutoScalingClient.builder().region(region).build()
//List asgNames = ['appeals-preprod-efolder-main-b']
//DescribeAutoScalingGroupsResponse describeAutoScalingGroups = asg.describeAutoScalingGroups(DescribeAutoScalingGroupsRequest.builder().autoScalingGroupNames('appeals-preprod-efolder-main-b').build())
//asg_instances = describeAutoScalingGroups.autoScalingGroups()[0].instances()
//println asg_instances
//println asg_instances.size()

class AutoScalingGroups {
  AutoScalingClient asgClient
  DescribeAutoScalingGroupsResponse autoScalingGroups
  List asgInstances
  String asgNames
  Region region = Region.US_GOV_WEST_1

  AutoScalingGroups(asgNames) {
    this.asgNames = asgNames
    this.asgClient = AutoScalingClient
                       .builder()
                       .region(this.region)
                       .build()
  }

  void updateAsgs() {
    this.autoScalingGroups = asgClient.describeAutoScalingGroups(
                              DescribeAutoScalingGroupsRequest
                                .builder()
                                .autoScalingGroupNames('appeals-preprod-efolder-main-b')
                                .build())
  }

  Integer instancesInAllAsgs() {
    this.updateAsgs()
    totalInstances = 0
    for asg in self.autoScalingGroups:
      totalInstances += asg.instances().size()
    return totalInstances
  }
}
