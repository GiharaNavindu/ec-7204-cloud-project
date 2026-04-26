targetScope = 'resourceGroup'

@description('Azure region for all resources.')
param location string = resourceGroup().location

@description('Name of the Azure Container Apps managed environment.')
param containerAppsEnvName string = 'aca-auction-env'

@description('Name of the Log Analytics workspace used by Container Apps.')
param logAnalyticsWorkspaceName string = 'law-aca-auction'

@description('Name of the virtual network used by the Container Apps environment.')
param vnetName string = 'vnet-aca-auction'

@description('CIDR block for the virtual network.')
param vnetAddressPrefix string = '10.42.0.0/16'

@description('Subnet name delegated to Azure Container Apps.')
param infrastructureSubnetName string = 'snet-aca-infra'

@description('Subnet CIDR block delegated to Azure Container Apps. Use /23 or larger.')
param infrastructureSubnetPrefix string = '10.42.0.0/23'

@description('Azure Container Registry login server, for example myregistry.azurecr.io.')
param acrLoginServer string

@description('Azure Container Registry username.')
param acrUsername string

@secure()
@description('Azure Container Registry password.')
param acrPassword string

@description('Image tag to deploy for api-gateway and user-service.')
param imageTag string = 'latest'

@description('Container App name for api-gateway.')
param apiGatewayAppName string = 'api-gateway'

@description('Container App name for user-service.')
param userServiceAppName string = 'user-service'

@description('Container App name for discovery-server, if you reintroduce it later.')
param discoveryServerAppName string = 'discovery-server'

@description('Spring Cloud Gateway container port.')
param apiGatewayPort int = 8080

@description('User Service container port.')
param userServicePort int = 8081

@description('Discovery server container port, kept as an optional toggle for future use.')
param discoveryServerPort int = 8761

@description('If true, also deploy the discovery-server Container App. Defaults to false because the current codebase no longer includes discovery-server.')
param deployDiscoveryServer bool = false

@description('Discovery-server image name and tag. Leave empty unless deployDiscoveryServer is true and you have reintroduced the service.')
param discoveryServerImage string = ''

@secure()
@description('JWT secret shared by api-gateway and user-service. Must be at least 64 characters for HS512.')
param jwtSecret string

@description('JWT expiration in milliseconds.')
param jwtExpiration string = '86400000'

@description('JDBC URL for the user service database. Point this at your managed PostgreSQL instance.')
param dbUrl string

@description('Database username used by user-service.')
param dbUsername string

@secure()
@description('Database password used by user-service.')
param dbPassword string

var apiGatewayImage = '${acrLoginServer}/api-gateway:${imageTag}'
var userServiceImage = '${acrLoginServer}/user-service:${imageTag}'
var discoveryImage = empty(discoveryServerImage) ? '' : discoveryServerImage

resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: logAnalyticsWorkspaceName
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
  }
}

resource vnet 'Microsoft.Network/virtualNetworks@2023-09-01' = {
  name: vnetName
  location: location
  properties: {
    addressSpace: {
      addressPrefixes: [
        vnetAddressPrefix
      ]
    }
    subnets: [
      {
        name: infrastructureSubnetName
        properties: {
          addressPrefix: infrastructureSubnetPrefix
          delegations: [
            {
              name: 'acaDelegation'
              properties: {
                serviceName: 'Microsoft.App/environments'
              }
            }
          ]
        }
      }
    ]
  }
}

resource containerAppsEnv 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: containerAppsEnvName
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalytics.properties.customerId
        sharedKey: logAnalytics.listKeys().primarySharedKey
      }
    }
    vnetConfiguration: {
      infrastructureSubnetId: resourceId(
        'Microsoft.Network/virtualNetworks/subnets',
        vnet.name,
        infrastructureSubnetName
      )
    }
  }
}

resource apiGatewayApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: apiGatewayAppName
  location: location
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    managedEnvironmentId: containerAppsEnv.id
    configuration: {
      registries: [
        {
          server: acrLoginServer
          username: acrUsername
          passwordSecretRef: 'acr-password'
        }
      ]
      secrets: [
        {
          name: 'acr-password'
          value: acrPassword
        }
        {
          name: 'jwt-secret'
          value: jwtSecret
        }
      ]
      ingress: {
        external: true
        targetPort: apiGatewayPort
        transport: 'auto'
        allowInsecure: false
      }
    }
    template: {
      containers: [
        {
          name: 'api-gateway'
          image: apiGatewayImage
          env: [
            {
              name: 'USER_SERVICE_URL'
              value: 'http://${userServiceApp.properties.configuration.ingress.fqdn}'
            }
            {
              name: 'JWT_SECRET'
              secretRef: 'jwt-secret'
            }
          ]
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 3
      }
    }
  }
}

resource userServiceApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: userServiceAppName
  location: location
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    managedEnvironmentId: containerAppsEnv.id
    configuration: {
      registries: [
        {
          server: acrLoginServer
          username: acrUsername
          passwordSecretRef: 'acr-password'
        }
      ]
      secrets: [
        {
          name: 'acr-password'
          value: acrPassword
        }
        {
          name: 'db-password'
          value: dbPassword
        }
        {
          name: 'jwt-secret'
          value: jwtSecret
        }
      ]
      ingress: {
        external: false
        targetPort: userServicePort
        transport: 'auto'
        allowInsecure: false
      }
    }
    template: {
      containers: [
        {
          name: 'user-service'
          image: userServiceImage
          env: [
            {
              name: 'DB_URL'
              value: dbUrl
            }
            {
              name: 'DB_USERNAME'
              value: dbUsername
            }
            {
              name: 'DB_PASSWORD'
              secretRef: 'db-password'
            }
            {
              name: 'JWT_SECRET'
              secretRef: 'jwt-secret'
            }
            {
              name: 'JWT_EXPIRATION'
              value: jwtExpiration
            }
          ]
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 3
      }
    }
  }
}

resource discoveryServerApp 'Microsoft.App/containerApps@2024-03-01' = if (deployDiscoveryServer && !empty(discoveryImage)) {
  name: discoveryServerAppName
  location: location
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    managedEnvironmentId: containerAppsEnv.id
    configuration: {
      registries: [
        {
          server: acrLoginServer
          username: acrUsername
          passwordSecretRef: 'acr-password'
        }
      ]
      secrets: [
        {
          name: 'acr-password'
          value: acrPassword
        }
      ]
      ingress: {
        external: false
        targetPort: discoveryServerPort
        transport: 'auto'
        allowInsecure: false
      }
    }
    template: {
      containers: [
        {
          name: 'discovery-server'
          image: discoveryImage
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 2
      }
    }
  }
}

output containerAppsEnvironmentId string = containerAppsEnv.id
output apiGatewayPublicUrl string = 'https://${apiGatewayApp.properties.configuration.ingress.fqdn}'
output userServiceInternalFqdn string = userServiceApp.properties.configuration.ingress.fqdn
output discoveryServerInternalFqdn string = deployDiscoveryServer && !empty(discoveryImage)
  ? '${discoveryServerAppName} (internal ingress enabled)'
  : ''
