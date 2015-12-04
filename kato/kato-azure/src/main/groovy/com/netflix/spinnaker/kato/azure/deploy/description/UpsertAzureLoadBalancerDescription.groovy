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

package com.netflix.spinnaker.kato.azure.deploy.description

import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials

class UpsertAzureLoadBalancerDescription {
  String cloudProvider
  String providerType
  String appName
  String loadBalancerName
  String stack
  String detail
  AzureCredentials credentials
  String region
  String vnet
  List<AzureLoadBalancerProbes> probes
  String securityGroups
  List<AzureLoadBalancingRules> loadBalancingRules
  List<AzureLoadBalancerInboundNATRules> inboundNATRules
  String name
  String user


  static class AzureLoadBalancerProbes {
    enum AzureLoadBalancerProbesType {
      HTTP, TCP
    }

    String probeName
    AzureLoadBalancerProbesType probeProtocol
    Integer probePort
    String probePath
    Integer probeInterval
    Integer unhealthyThreshold
  }

  static class AzureLoadBalancingRules {
    enum AzureLoadBalancingRulesType {
      TCP, UDP
    }

    String ruleName
    AzureLoadBalancingRulesType protocol
    Integer externalPort
    Integer backendPort
    String probeName
    String persistence
    Integer idleTimeout
  }

  static class AzureLoadBalancerInboundNATRules {
    enum AzureLoadBalancerInboundNATRulesProtocolType {
      HTTP, TCP
    }
    enum AzureLoadBalancerInboundNATRulesServiceType {
      SSH
    }

    String ruleName
    AzureLoadBalancerInboundNATRulesServiceType serviceType
    AzureLoadBalancerInboundNATRulesProtocolType protocol
    Integer port
  }
}