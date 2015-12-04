/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.oort.azure.controllers

import com.fasterxml.jackson.annotation.JsonProperty
import com.microsoft.azure.management.network.models.LoadBalancer
import com.netflix.spinnaker.clouddriver.azure.client.models.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.azure.security.AzureNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.azure.client.AzureNetworkClient
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.oort.azure.model.AzureLoadBalancer
import com.netflix.spinnaker.oort.azure.model.AzureResourceRetriever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import com.microsoft.azure.management.network.models.LoadBalancer


@RestController
@RequestMapping("/azure/loadBalancers")
class AzureLoadBalancerController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AzureResourceRetriever azureResourceRetriever

  @RequestMapping(method = RequestMethod.GET)
  List<AzureLoadBalancerSummary> list() {
    return getSummaryForLoadBalancers(azureResourceRetriever.applicationLoadBalancerMap).values() as List
  }

  private Map<String, AzureLoadBalancerSummary> getSummaryForLoadBalancers(Map<String, Map<String, Set<LoadBalancer>>> loadBalancerMap) {
    Map<String, AzureLoadBalancerSummary> map = [:]

    loadBalancerMap?.each() { account, appMap ->
      appMap.each() { application, loadBalancerList ->
        loadBalancerList.each() { AzureLoadBalancerDescription loadBalancer ->

          def summary = map.get(loadBalancer.loadBalancerName)

          if (!summary) {
            summary = new AzureLoadBalancerSummary(name: loadBalancer.loadBalancerName)
            map.put loadBalancer.loadBalancerName, summary
          }

          def loadBalancerDetail = new AzureLoadBalancerDetail(account: account, name: loadBalancer.loadBalancerName, region: loadBalancer.region)

          summary.getOrCreateAccount(account).getOrCreateRegion(loadBalancer.region).loadBalancers << loadBalancerDetail

        }
      }
    }
    map
  }

  @RequestMapping(value = "/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  List<Map> getDetailsInAccountAndRegionByName(@PathVariable String account, @PathVariable String region, @PathVariable String name) {
    String appName = azureResourceRetriever.getAppNameFromLoadBalancer(name)

    AzureLoadBalancerDescription azureLoadBalancerDescription = azureResourceRetriever.getLoadBalancer(account, appName, name)

    if (azureLoadBalancerDescription) {
      def lbDetail = [
        name: azureLoadBalancerDescription.loadBalancerName
      ]

      if (azureLoadBalancerDescription.createdTime) {
        lbDetail.createdTime = azureLoadBalancerDescription.createdTime
      }
      else {
        // return a constant in order to test the "deck" details view for a given load balancer
        lbDetail.createdTime = 1448944139570
      }

      if (azureLoadBalancerDescription.vnet) {
        lbDetail.vnet = azureLoadBalancerDescription.vnet
      }
      else {
        // return a constant in order to test the "deck" details view for a given load balancer
        lbDetail.vnet = "vnet-unassigned"
      }

      lbDetail.probes = azureLoadBalancerDescription.probes

      if (azureLoadBalancerDescription.securityGroups) {
        lbDetail.securityGroups = azureLoadBalancerDescription.securityGroups
      }

      if (azureLoadBalancerDescription.loadBalancingRules) {
        lbDetail.loadBalancingRules = azureLoadBalancerDescription.loadBalancingRules
      }

      if (azureLoadBalancerDescription.inboundNATRules) {
        lbDetail.inboundNATRules = azureLoadBalancerDescription.inboundNATRules
      }

      if (azureLoadBalancerDescription.dnsName) {
        lbDetail.dnsName = azureLoadBalancerDescription.dnsName
      }
      else {
        // return a constant in order to test the "deck" details view for a given load balancer
        lbDetail.dnsName = "dnsname-unassigned"
      }

      return [lbDetail]
    }

    return []
  }

  static class AzureLoadBalancerSummary {
    private Map<String, AzureLoadBalancerAccount> mappedAccounts = [:]
    String name

    AzureLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new AzureLoadBalancerAccount(name:name))
      }

      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<AzureLoadBalancerAccount> getAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class AzureLoadBalancerAccount {
    private Map<String, AzureLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    AzureLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new AzureLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name];
    }

    @JsonProperty("regions")
    List<AzureLoadBalancerAccountRegion> getRegions() {
      mappedRegions.values() as List
    }

  }

  static class AzureLoadBalancerAccountRegion {
    String name
    List<AzureLoadBalancerDetail> loadBalancers
  }

  static class AzureLoadBalancerDetail {
    String account
    String region
    String name
    String type="azure"
  }
}
