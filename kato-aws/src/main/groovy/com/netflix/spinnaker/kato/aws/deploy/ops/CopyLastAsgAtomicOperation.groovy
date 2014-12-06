/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.*
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAmazonCredentials
import com.netflix.spinnaker.kato.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.aws.deploy.AutoScalingWorker
import com.netflix.spinnaker.kato.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.kato.aws.deploy.handlers.BasicAmazonDeployHandler
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import org.springframework.beans.factory.annotation.Autowired

import java.util.regex.Pattern

class CopyLastAsgAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final Pattern SG_PATTERN = Pattern.compile(/^sg-[0-9a-f]+$/)
  private static final String BASE_PHASE = "COPY_LAST_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  BasicAmazonDeployHandler basicAmazonDeployHandler

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Autowired
  ObjectMapper objectMapper

  final BasicAmazonDeployDescription description

  CopyLastAsgAtomicOperation(BasicAmazonDeployDescription description) {
    this.description = description
  }

  @Override
  DeploymentResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Copy Last ASG Operation..."

    DeploymentResult result = new DeploymentResult()
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      def targetRegion = entry.key

      def ancestorAsg
      def sourceRegion
      def sourceAsgCredentials
      if (description.source.account && description.source.region && description.source.asgName) {
        sourceRegion = description.source.region
        sourceAsgCredentials = accountCredentialsProvider.getCredentials(description.source.account) as NetflixAssumeRoleAmazonCredentials
        def sourceAutoScaling = amazonClientProvider.getAutoScaling(sourceAsgCredentials, sourceRegion)
        def request = new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: [description.source.asgName])
        List<AutoScalingGroup> ancestorAsgs = sourceAutoScaling.describeAutoScalingGroups(request).autoScalingGroups
        ancestorAsg = ancestorAsgs.isEmpty() ? null : ancestorAsgs[0]
      } else {
        sourceRegion = targetRegion
        sourceAsgCredentials = description.credentials
        task.updateStatus BASE_PHASE, "Looking up last ASG in ${sourceRegion} for ${description.application}-${description.stack}."
        ancestorAsg = getAncestorAsg(sourceRegion)
      }
      def sourceRegionScopedProvider = regionScopedProviderFactory.forRegion(sourceAsgCredentials, sourceRegion)

      def ancestorLaunchConfiguration = getLaunchConfiguration(sourceRegion, ancestorAsg.launchConfigurationName)
      BasicAmazonDeployDescription newDescription = description.clone()

      if (ancestorAsg.VPCZoneIdentifier) {
        task.updateStatus BASE_PHASE, "Looking up subnet type..."
        newDescription.subnetType = getPurposeForSubnet(sourceRegion, ancestorAsg.VPCZoneIdentifier.tokenize(',').getAt(0))
        task.updateStatus BASE_PHASE, "Found: ${newDescription.subnetType}."
      }

      newDescription.iamRole = description.iamRole ?: ancestorLaunchConfiguration.iamInstanceProfile
      newDescription.amiName = description.amiName ?: ancestorLaunchConfiguration.imageId
      newDescription.availabilityZones = [(targetRegion): description.availabilityZones[targetRegion] ?: ancestorAsg.availabilityZones]
      newDescription.instanceType = description.instanceType ?: ancestorLaunchConfiguration.instanceType
      newDescription.loadBalancers = description.loadBalancers != null ? description.loadBalancers : ancestorAsg.loadBalancerNames
      newDescription.securityGroups = getSecurityGroupNamesForIds(sourceRegion, description.securityGroups != null ? description.securityGroups : ancestorLaunchConfiguration.securityGroups)
      newDescription.capacity.min = description.capacity?.min != null ? description.capacity.min : ancestorAsg.minSize
      newDescription.capacity.max = description.capacity?.max != null ? description.capacity.max : ancestorAsg.maxSize
      newDescription.capacity.desired = description.capacity?.desired != null ? description.capacity.desired : ancestorAsg.desiredCapacity
      newDescription.keyPair = description.keyPair ?: ancestorLaunchConfiguration.keyName
      newDescription.blockDevices = description.blockDevices != null ? description.blockDevices : convertBlockDevices(ancestorLaunchConfiguration.blockDeviceMappings)
      newDescription.associatePublicIpAddress = description.associatePublicIpAddress != null ? description.associatePublicIpAddress : ancestorLaunchConfiguration.associatePublicIpAddress
      newDescription.cooldown = description.cooldown != null ? description.cooldown : ancestorAsg.defaultCooldown
      newDescription.healthCheckGracePeriod = description.healthCheckGracePeriod != null ? description.healthCheckGracePeriod : ancestorAsg.healthCheckGracePeriod
      newDescription.healthCheckType = description.healthCheckType ?: ancestorAsg.healthCheckType
      newDescription.spotPrice = description.spotPrice != null ? description.spotPrice : ancestorLaunchConfiguration.spotPrice
      newDescription.suspendedProcesses = description.suspendedProcesses != null ? description.suspendedProcesses : ancestorAsg.suspendedProcesses*.processName
      newDescription.terminationPolicies = description.terminationPolicies != null ? description.terminationPolicies : ancestorAsg.terminationPolicies
      newDescription.ramdiskId = description.ramdiskId ?: (ancestorLaunchConfiguration.ramdiskId ?: null)
      newDescription.instanceMonitoring = description.instanceMonitoring != null ? description.instanceMonitoring : ancestorLaunchConfiguration.instanceMonitoring
      newDescription.ebsOptimized = description.ebsOptimized != null ? description.ebsOptimized : ancestorLaunchConfiguration.ebsOptimized

      task.updateStatus BASE_PHASE, "Initiating deployment."
      def thisResult = basicAmazonDeployHandler.handle(newDescription, priorOutputs)
      def newAsgName = thisResult.asgNameByRegion[targetRegion]
      def asgReferenceCopier = sourceRegionScopedProvider.getAsgReferenceCopier(description.credentials, targetRegion)
      asgReferenceCopier.copyScalingPoliciesWithAlarms(ancestorAsg.autoScalingGroupName, newAsgName)
      asgReferenceCopier.copyScheduledActionsForAsg(ancestorAsg.autoScalingGroupName, newAsgName)

      result.serverGroupNames.addAll(thisResult.serverGroupNames)
      result.messages.addAll(thisResult.messages)
      task.updateStatus BASE_PHASE, "Deployment complete in $targetRegion. New ASGs = ${result.serverGroupNames}"
    }
    task.updateStatus BASE_PHASE, "Finished copying last ASG for ${description.application}-${description.stack}. New ASGs = ${result.serverGroupNames}."

    result
  }

  AutoScalingGroup getAncestorAsg(String region) {
    def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
    def autoScalingWorker = new AutoScalingWorker(application: description.application, stack: description.stack, autoScaling: autoScaling, region: region,
      environment: description.credentials.name)
    autoScalingWorker.ancestorAsg
  }

  LaunchConfiguration getLaunchConfiguration(String region, String launchConfigurationName) {
    def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
    def result = autoScaling.describeLaunchConfigurations(new DescribeLaunchConfigurationsRequest().withLaunchConfigurationNames(launchConfigurationName))
    result.launchConfigurations?.getAt(0)
  }

  String getPurposeForSubnet(String region, String subnetId) {
    def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region)
    def result = amazonEC2.describeSubnets(new DescribeSubnetsRequest().withSubnetIds(subnetId))
    def json = result.subnets?.getAt(0)?.tags?.find { it.key == AutoScalingWorker.SUBNET_METADATA_KEY }?.value
    def metadata = objectMapper.readValue(json, Map)
    (metadata && metadata.purpose) ? metadata.purpose : null
  }

  List<String> getSecurityGroupNamesForIds(String region, List<String> ids) {
    def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region)

    def (List<String> groupIds, List<String> groupNames) = ids.split { SG_PATTERN.matcher(it).matches() }
    def result = amazonEC2.describeSecurityGroups(new DescribeSecurityGroupsRequest().withGroupIds(groupIds).withGroupNames(groupNames))
    result.securityGroups*.groupName
  }

  List<AmazonBlockDevice> convertBlockDevices(List<BlockDeviceMapping> blockDeviceMappings) {
    blockDeviceMappings.collect {
      def device = new AmazonBlockDevice(deviceName: it.deviceName, virtualName: it.virtualName)
      it.ebs?.with {
        device.iops = iops
        device.deleteOnTermination = deleteOnTermination
        device.size = volumeSize
        device.volumeType = volumeType
        device.snapshotId = snapshotId
      }
      device
    }
  }
}