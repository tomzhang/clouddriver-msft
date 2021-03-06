/*
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spinnaker.kato.cf.deploy.description
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.kato.deploy.DeployDescription
import groovy.transform.AutoClone
import groovy.transform.Canonical
/**
 * Descriptor for a Cloud Foundry {@link DeployDescription}
 *
 *
 */
@AutoClone
@Canonical
@JsonIgnoreProperties(ignoreUnknown = true)
class CloudFoundryDeployDescription implements DeployDescription {

  String application

  Map<String, List<String>> availabilityZones

  Map<String, Double> capacity

  @JsonProperty("securityGroups")
  List<String> services

  @JsonProperty("zone")
  String space

  Integer targetSize

  List<String> urls = []

  Integer memory = 1024

  String stack

  String freeFormDetails

  Boolean trustSelfSignedCerts = true

  @JsonIgnore
  CloudFoundryAccountCredentials credentials

  @JsonProperty("credentials")
  String getCredentialAccount() {
    this.credentials.name
  }

  String serverGroupName

  String strategy

  Map<String, Object> trigger

  /**
   * Repository-specific details
   */

  String repository
  String artifact
  String username
  String password

}
