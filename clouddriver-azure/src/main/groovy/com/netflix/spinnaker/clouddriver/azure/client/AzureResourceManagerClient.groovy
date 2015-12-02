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

package com.netflix.spinnaker.clouddriver.azure.client

import com.microsoft.azure.management.resources.ResourceManagementClient
import com.microsoft.azure.management.resources.ResourceManagementService
import com.microsoft.azure.management.resources.models.Deployment
import com.microsoft.azure.management.resources.models.DeploymentExtended
import com.microsoft.azure.management.resources.models.DeploymentMode
import com.microsoft.azure.management.resources.models.DeploymentProperties
import com.microsoft.azure.management.resources.models.ResourceGroup
import com.microsoft.azure.management.resources.models.ResourceGroupExtended
import com.microsoft.azure.management.resources.models.ResourceGroupListParameters
import com.microsoft.azure.management.resources.models.ResourceGroupListResult
import com.microsoft.azure.utility.ResourceHelper
import com.microsoft.windowsazure.exception.ServiceException
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import com.google.gson.Gson

import groovy.transform.CompileStatic

@CompileStatic
class AzureResourceManagerClient extends AzureBaseClient {
  // URI to the internal load balancer template. we should create our own public and private LB templates and store them in a local cache.
  private String loadBalancerTemplateUri = "https://raw.githubusercontent.com/Azure/azure-quickstart-templates/master/101-create-internal-loadbalancer/azuredeploy.json"

  public AzureResourceManagerClient(String subscriptionId) {
    super(subscriptionId)
  }

  public String createLoadBalancerFromTemplate(AzureCredentials credentials,
                                               String template,
                                               String resourceGroupName,
                                               String region,
                                               String loadBalancerName) {
    Map<String, String> parameters = new HashMap<String, String>();
    parameters.put("location", region);
    createLoadBalancerFromTemplate(credentials, template, parameters, resourceGroupName, region, loadBalancerName)
  }

  public String createLoadBalancerFromTemplate(AzureCredentials credentials,
                                               String template,
                                               Map<String, String> templateParams,
                                               String resourceGroupName,
                                               String region,
                                               String loadBalancerName) {
    if (!resourceGroupExists(credentials, resourceGroupName)) {
      createResouceGroup(credentials, resourceGroupName, region)
    }

    String deploymentName = loadBalancerName + "_deployment"

    DeploymentExtended deployment = createTemplateDeploymentFromPath(this.getResourceManagementClient(credentials),
                                                                     resourceGroupName,
								     DeploymentMode.Incremental,
								     deploymentName,
								     template,
								     templateParams)

    return deployment.properties.provisioningState

  }

  public ResourceGroup createResouceGroup(AzureCredentials creds, String resourceGroupName, String region) {
    ResourceGroup rg = new ResourceGroup(region)
    try {
      return this.getResourceManagementClient(creds).getResourceGroupsOperations().createOrUpdate(resourceGroupName, rg).resourceGroup
    } catch (e) {
      throw new RuntimeException("Unable to create Resource Group ${resourceGroupName} in region ${region}", e)
    }
  }

  public ArrayList<ResourceGroup> getResourcesGroupsForApp(AzureCredentials creds, String applicationName) {
    ResourceGroupListParameters parameters = new ResourceGroupListParameters()
    parameters.setTagName("filter")
    parameters.setTagValue(applicationName)
    return this.getResourceManagementClient(creds).getResourceGroupsOperations().list(parameters).resourceGroups
  }

  public ArrayList<ResourceGroupExtended> getAllResourceGroups(AzureCredentials creds) {
    return this.getResourceManagementClient(creds).getResourceGroupsOperations().list(null).getResourceGroups()
  }

  public boolean resourceGroupExists(AzureCredentials creds, String resourceGroupName) {
    return this.getResourceManagementClient(creds).getResourceGroupsOperations().checkExistence(resourceGroupName).isExists()
  }

  public void healthCheck(AzureCredentials creds) throws Exception {
    try {
      this.getResourceManagementClient(creds).getResourcesOperations().list(null)
    }
    catch (Exception e) {
      throw new Exception("Unable to ping Azure", e)
    }
  }

  protected ResourceManagementClient getResourceManagementClient(AzureCredentials creds) {
    return ResourceManagementService.create(this.buildConfiguration(creds))
  }

  public static DeploymentExtended createTemplateDeploymentFromPath(
    ResourceManagementClient resourceManagementClient,
    String resourceGroupName,
    DeploymentMode deploymentMode,
    String deploymentName,
    String template,
    Map<String, String> templateParameters) throws URISyntaxException, IOException, ServiceException {

    DeploymentProperties deploymentProperties = new DeploymentProperties();
    deploymentProperties.setMode(deploymentMode);

    // set the link to template JSON
    //TemplateLink templateLink = new TemplateLink(new URI(templateUri));
    //templateLink.setContentVersion(templateContentVersion);
    deploymentProperties.setTemplate(template);

    // initialize the parameters for this template
    if (templateParameters) {
      Map<String, ParameterValue> parameters = new HashMap<String, ParameterValue>();
      for (Map.Entry<String, String> entry : templateParameters.entrySet()) {
        parameters.put(entry.getKey(), new ParameterValue(entry.getValue()));
      }
      deploymentProperties.setParameters(new Gson().toJson(parameters));
    }

    // kick off the deployment
    Deployment deployment = new Deployment();
    deployment.setProperties(deploymentProperties);

    return resourceManagementClient
      .getDeploymentsOperations()
      .createOrUpdate(resourceGroupName, deploymentName, deployment)
      .getDeployment();
  }

  private static class ParameterValue {
    String value;
    ParameterValue(String value) {
      this.value = value;
    }
  }

  /*
public String createLoadBalancer(AzureCredentials creds,
                                 String resourceGroupName,
                                 String loadBalancerName,
                                 String region) {
  String templateVersion = "1.0.0.0"

  try {
    if (!resourceGroupExists(creds, resourceGroupName)) {
      createResouceGroup(creds, resourceGroupName, region)
    }

    Map<String, String> templateParams = new HashMap<String, String>()
    templateParams.put("loadBalancerName", loadBalancerName)
    templateParams.put("location", region)
    templateParams.put("addressPrefix", "10.0.0.0/16")
    templateParams.put("subnetPrefix", "10.0.0.0/24")

    DeploymentExtended deployment = ResourceHelper.createTemplateDeploymentFromURI(
      this.getResourceManagementClient(creds),
      resourceGroupName,
      DeploymentMode.Incremental,
      loadBalancerName,
      String.format(loadBalancerTemplatePath, loadBalancerTemplatName),
      templateVersion,
      templateParams
    )

    return deployment.name
  } catch (e) {
    throw new RuntimeException("Unable to create load balancer ${loadBalancerName}", e)
  }
} */

  /*
  public String createLoadBalancer(AzureCredentials credentials,
                                   String resourceGroupName,
                                   String loadBalancerName,
                                   String region) {
    return createLoadBalancer(credentials, resourceGroupName, loadBalancerName, region, "stacktest", "spinnaker3");
  }

  public String deleteLoadBalancer(AzureCredentials credentials,
                                   String resourceGroupName,
                                   String loadBalancerName,
                                   String region) {
    return "";
  }

  public String createLoadBalancer(AzureCredentials credentials, String resourceGroupName, String loadBalancerName, String region, String stackName, String dnsName)
  {
    try {

      Map<String, String> templateParams = getTemplateParameters(loadBalancerName, region, stackName, dnsName)

      return createLoadBalanacerFromTemplate(credentials, getLoadBalancerTemplate(), templateParams, resourceGroupName, region)
    }
    catch (Exception e){
      throw new Exception(String.format("Unable to create load balancer {0}", loadBalancerName))
    }
  } */


  /*
  private String loadBalancerTemplatName = "loadBlanacer.json";
  private String loadBalancerTemplatePath = "/home/scotm/source/repos/haishi/clouddriver/clouddriver-azure/templates/%s"; //System.getenv("AZURE_LB_TEMPLATE");

  private String getLoadBalancerTemplate() {
    String content
    String templatePath = System.getenv("AZURE_LOADBALANCER_TEMPLATE_PATH")
    if (templatePath == null || templatePath.isEmpty()) {
      content = loadBalancerTemplateString
    } else {
      Path p = Paths.get(templatePath)
      if (!Files.exists(p))
      {
        content = loadBalancerTemplateString
      }
      else {
        byte[] fileArray = Files.readAllBytes(p)
        content = new String(fileArray);
      }
    }

    content
  }

  private static Map<String, String> getTemplateParameters(String lbName, String region, String stackName, String dnsName)
  {
    Map<String, String> parms = new HashMap<String, String>();
    parms.put("loadBalancerName", lbName);
    parms.put("location", region);
    parms.put("stackName",stackName);
    parms.put("dnsNameforLBIP", dnsName);

    return parms;
  }
  */

  /*
  // Example of how an Azure Load Balancer deploy template should look like
  
  private static String loadBalancerTemplateString = "{\n" +
    "  \"\$schema\": \"https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#\",\n" +
    "  \"contentVersion\": \"1.0.0.0\",\n" +
    "  \"parameters\": {\n" +
    "    \"dnsNameforLBIP\": {\n" +
    "      \"type\": \"string\",\n" +
    "      \"metadata\": {\n" +
    "        \"description\": \"Unique DNS name\"\n" +
    "      }\n" +
    "    },\n" +
    "    \"stackName\": {\n" +
    "      \"type\": \"string\"\n" +
    "    },\n" +
    "    \"location\": {\n" +
    "      \"type\": \"string\"\n" +
    "    },\n" +
    "    \"publicIPAddressType\": {\n" +
    "      \"type\": \"string\",\n" +
    "      \"defaultValue\": \"Dynamic\",\n" +
    "      \"allowedValues\": [\n" +
    "        \"Dynamic\",\n" +
    "        \"Static\"\n" +
    "      ]\n" +
    "    },\n" +
    "    \"loadBalancerName\": {\n" +
    "      \"type\": \"string\",\n" +
    "      \"defaultValue\": \"loadBalancer1\"\n" +
    "    }\n" +
    "  },\n" +
    "  \"variables\": {\n" +
    "    \"publicIPAddressName\": \"publicIp1\",\n" +
    "    \"publicIPAddressID\": \"[resourceId('Microsoft.Network/publicIPAddresses',variables('publicIPAddressName'))]\"\n" +
    "  },\n" +
    "  \"resources\": [\n" +
    "    {\n" +
    "      \"apiVersion\": \"2015-05-01-preview\",\n" +
    "      \"type\": \"Microsoft.Network/publicIPAddresses\",\n" +
    "      \"name\": \"[variables('publicIPAddressName')]\",\n" +
    "      \"location\": \"[parameters('location')]\",\n" +
    "      \"properties\": {\n" +
    "        \"publicIPAllocationMethod\": \"[parameters('publicIPAddressType')]\",\n" +
    "        \"dnsSettings\": {\n" +
    "          \"domainNameLabel\": \"[parameters('dnsNameforLBIP')]\"\n" +
    "        }\n" +
    "      }\n" +
    "    },\n" +
    "    {\n" +
    "      \"apiVersion\": \"2015-05-01-preview\",\n" +
    "      \"name\": \"[parameters('loadBalancerName')]\",\n" +
    "      \"type\": \"Microsoft.Network/loadBalancers\",\n" +
    "      \"location\": \"[parameters('location')]\",\n" +
    "      \"tags\": {\n" +
    "        \"stack\": \"[parameters('stackName')]\"\n" +
    "      },\n" +
    "      \"dependsOn\": [\n" +
    "        \"[concat('Microsoft.Network/publicIPAddresses/', variables('publicIPAddressName'))]\"\n" +
    "      ],\n" +
    "      \"properties\": {\n" +
    "        \"frontendIPConfigurations\": [\n" +
    "          {\n" +
    "            \"name\": \"loadBalancerFrontEnd\",\n" +
    "            \"properties\": {\n" +
    "              \"publicIPAddress\": {\n" +
    "                \"id\": \"[variables('publicIPAddressID')]\"\n" +
    "              }\n" +
    "            }\n" +
    "          }\n" +
    "        ],\n" +
    "        \"backendAddressPools\": [\n" +
    "          {\n" +
    "            \"name\": \"loadBalancerBackEnd\"\n" +
    "          }\n" +
    "        ]\n" +
    "      }\n" +
    "    }\n" +
    "  ]\n" +
    "}"*/

}
