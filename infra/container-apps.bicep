param location string = resourceGroup().location
param environmentName string = 'env-ruhuna-auction'
param logAnalyticsWorkspaceName string = 'log-ruhuna-auction'
param acrLoginServer string
param acrUsername string
@secure()
param acrPassword string

param postgresqlServerName string
param postgresqlAdminUsername string
@secure()
param postgresqlAdminPassword string

param redisCacheName string

@secure()
param jwtSecret string
@secure()
param dbPassword string

param rabbitmqHost string
param rabbitmqUsername string
@secure()
param rabbitmqPassword string

param googleClientId string
@secure()
param googleClientSecret string
param appOAuth2RedirectUri string = ''
param zipkinUrl string = ''

param imageTag string = 'latest'

// Log Analytics Workspace
resource logAnalyticsWorkspace 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: logAnalyticsWorkspaceName
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
  }
}

// Container App Environment
resource containerAppEnv 'Microsoft.App/managedEnvironments@2023-05-01' = {
  name: environmentName
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalyticsWorkspace.properties.customerId
        sharedKey: logAnalyticsWorkspace.listKeys().primarySharedKey
      }
    }
  }
}

// PostgreSQL Flexible Server
resource postgresServer 'Microsoft.DBforPostgreSQL/flexibleServers@2022-12-01' = {
  name: postgresqlServerName
  location: location
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    administratorLogin: postgresqlAdminUsername
    administratorLoginPassword: postgresqlAdminPassword
    version: '16'
    storage: {
      storageSizeGB: 128
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
  }
}

resource postgresFirewall 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2022-12-01' = {
  parent: postgresServer
  name: 'AllowAllAzureIPs'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

// Create Databases
resource userDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2022-12-01' = {
  parent: postgresServer
  name: 'user_db'
}
resource auctionDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2022-12-01' = {
  parent: postgresServer
  name: 'auction_db'
}
resource bidDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2022-12-01' = {
  parent: postgresServer
  name: 'bid_db'
}
resource notificationDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2022-12-01' = {
  parent: postgresServer
  name: 'notification_db'
}

// Redis Cache (Updated to Azure Managed Redis)
resource redisCache 'Microsoft.Cache/redisEnterprise@2024-09-01' = {
  name: redisCacheName
  location: location
  sku: {
    name: 'Balanced_B0'
  }
}

resource redisDb 'Microsoft.Cache/redisEnterprise/databases@2024-09-01' = {
  parent: redisCache
  name: 'default'
  properties: {
    clientProtocol: 'Plaintext'
    evictionPolicy: 'NoEviction'
    clusteringPolicy: 'EnterpriseCluster'
  }
}

// Container Apps
var registrySecretName = 'registry-password'

// Helper for common env vars
var commonEnv = [
  { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
  { name: 'ZIPKIN_URL', value: zipkinUrl }
]

// User Service
resource userService 'Microsoft.App/containerApps@2023-05-01' = {
  name: 'user-service'
  location: location
  properties: {
    managedEnvironmentId: containerAppEnv.id
    configuration: {
      secrets: [
        { name: registrySecretName, value: acrPassword }
        { name: 'db-password', value: dbPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'google-client-secret', value: googleClientSecret }
      ]
      registries: [
        {
          server: acrLoginServer
          username: acrUsername
          passwordSecretRef: registrySecretName
        }
      ]
      ingress: {
        external: false
        targetPort: 8081
        transport: 'auto'
      }
    }
    template: {
      containers: [
        {
          name: 'user-service'
          image: '${acrLoginServer}/user-service:${imageTag}'
          env: concat(commonEnv, [
            {
              name: 'DB_URL'
              value: 'jdbc:postgresql://${postgresServer.properties.fullyQualifiedDomainName}:5432/user_db'
            }
            { name: 'DB_USERNAME', value: postgresqlAdminUsername }
            { name: 'DB_PASSWORD', secretRef: 'db-password' }
            { name: 'GOOGLE_CLIENT_ID', value: googleClientId }
            { name: 'GOOGLE_CLIENT_SECRET', secretRef: 'google-client-secret' }
            { name: 'APP_OAUTH2_REDIRECT_URI', value: appOAuth2RedirectUri }
          ])
          resources: {
            cpu: json('0.25')
            memory: '0.5Gi'
          }
        }
      ]
    }
  }
}

// Auction Service
resource auctionService 'Microsoft.App/containerApps@2023-05-01' = {
  name: 'auction-service'
  location: location
  properties: {
    managedEnvironmentId: containerAppEnv.id
    configuration: {
      secrets: [
        { name: registrySecretName, value: acrPassword }
        { name: 'db-password', value: dbPassword }
        { name: 'jwt-secret', value: jwtSecret }
      ]
      registries: [
        {
          server: acrLoginServer
          username: acrUsername
          passwordSecretRef: registrySecretName
        }
      ]
      ingress: {
        external: false
        targetPort: 8082
        transport: 'auto'
      }
    }
    template: {
      containers: [
        {
          name: 'auction-service'
          image: '${acrLoginServer}/auction-service:${imageTag}'
          env: concat(commonEnv, [
            {
              name: 'DB_URL'
              value: 'jdbc:postgresql://${postgresServer.properties.fullyQualifiedDomainName}:5432/auction_db'
            }
            { name: 'DB_USERNAME', value: postgresqlAdminUsername }
            { name: 'DB_PASSWORD', secretRef: 'db-password' }
            { name: 'RABBITMQ_PORT', value: '5671' }
          ])
          resources: {
            cpu: json('0.25')
            memory: '0.5Gi'
          }
        }
      ]
    }
  }
}

// Bid Service
resource bidService 'Microsoft.App/containerApps@2023-05-01' = {
  name: 'bid-service'
  location: location
  properties: {
    managedEnvironmentId: containerAppEnv.id
    configuration: {
      secrets: [
        { name: registrySecretName, value: acrPassword }
        { name: 'db-password', value: dbPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'rabbitmq-password', value: rabbitmqPassword }
      ]
      registries: [
        {
          server: acrLoginServer
          username: acrUsername
          passwordSecretRef: registrySecretName
        }
      ]
      ingress: {
        external: false
        targetPort: 8083
        transport: 'auto'
      }
    }
    template: {
      containers: [
        {
          name: 'bid-service'
          image: '${acrLoginServer}/bid-service:${imageTag}'
          env: concat(commonEnv, [
            {
              name: 'DB_URL'
              value: 'jdbc:postgresql://${postgresServer.properties.fullyQualifiedDomainName}:5432/bid_db'
            }
            { name: 'DB_USERNAME', value: postgresqlAdminUsername }
            { name: 'DB_PASSWORD', secretRef: 'db-password' }
            { name: 'AUCTION_SERVICE_URL', value: 'http://${auctionService.properties.configuration.ingress.fqdn}' }
            { name: 'RABBITMQ_HOST', value: rabbitmqHost }
            { name: 'RABBITMQ_PORT', value: '5671' }
            { name: 'RABBITMQ_USERNAME', value: rabbitmqUsername }
            { name: 'RABBITMQ_PASSWORD', secretRef: 'rabbitmq-password' }
          ])
          resources: {
            cpu: json('0.25')
            memory: '0.5Gi'
          }
        }
      ]
    }
  }
}

// Notification Service
resource notificationService 'Microsoft.App/containerApps@2023-05-01' = {
  name: 'notification-service'
  location: location
  properties: {
    managedEnvironmentId: containerAppEnv.id
    configuration: {
      secrets: [
        { name: registrySecretName, value: acrPassword }
        { name: 'db-password', value: dbPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'rabbitmq-password', value: rabbitmqPassword }
      ]
      registries: [
        {
          server: acrLoginServer
          username: acrUsername
          passwordSecretRef: registrySecretName
        }
      ]
      ingress: {
        external: false
        targetPort: 8085
        transport: 'auto'
      }
    }
    template: {
      containers: [
        {
          name: 'notification-service'
          image: '${acrLoginServer}/notification-service:${imageTag}'
          env: concat(commonEnv, [
            {
              name: 'NOTIFICATION_DB_URL'
              value: 'jdbc:postgresql://${postgresServer.properties.fullyQualifiedDomainName}:5432/notification_db'
            }
            { name: 'DB_USERNAME', value: postgresqlAdminUsername }
            { name: 'DB_PASSWORD', secretRef: 'db-password' }
            { name: 'RABBITMQ_HOST', value: rabbitmqHost }
            { name: 'RABBITMQ_PORT', value: '5671' }
            { name: 'RABBITMQ_USERNAME', value: rabbitmqUsername }
            { name: 'RABBITMQ_PASSWORD', secretRef: 'rabbitmq-password' }
          ])
          resources: {
            cpu: json('0.25')
            memory: '0.5Gi'
          }
        }
      ]
    }
  }
}

// API Gateway
resource apiGateway 'Microsoft.App/containerApps@2023-05-01' = {
  name: 'api-gateway'
  location: location
  properties: {
    managedEnvironmentId: containerAppEnv.id
    configuration: {
      secrets: [
        { name: registrySecretName, value: acrPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'redis-password', value: redisDb.listKeys().primaryKey }
      ]
      registries: [
        {
          server: acrLoginServer
          username: acrUsername
          passwordSecretRef: registrySecretName
        }
      ]
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
      }
    }
    template: {
      containers: [
        {
          name: 'api-gateway'
          image: '${acrLoginServer}/api-gateway:${imageTag}'
          env: concat(commonEnv, [
            { name: 'USER_SERVICE_URL', value: 'http://${userService.properties.configuration.ingress.fqdn}' }
            { name: 'AUCTION_SERVICE_URL', value: 'http://${auctionService.properties.configuration.ingress.fqdn}' }
            { name: 'BID_SERVICE_URL', value: 'http://${bidService.properties.configuration.ingress.fqdn}' }
            {
              name: 'NOTIFICATION_SERVICE_URL'
              value: 'http://${notificationService.properties.configuration.ingress.fqdn}'
            }
            { name: 'REDIS_HOST', value: redisCache.properties.hostName }
            { name: 'REDIS_PORT', value: string(redisDb.properties.port) }
            { name: 'REDIS_PASSWORD', secretRef: 'redis-password' }
          ])
          resources: {
            cpu: json('0.25')
            memory: '0.5Gi'
          }
        }
      ]
    }
  }
}

output apiGatewayPublicUrl string = 'https://${apiGateway.properties.configuration.ingress.fqdn}'
output userServiceInternalFqdn string = userService.properties.configuration.ingress.fqdn
output auctionServiceInternalFqdn string = auctionService.properties.configuration.ingress.fqdn
output bidServiceInternalFqdn string = bidService.properties.configuration.ingress.fqdn
output notificationServiceInternalFqdn string = notificationService.properties.configuration.ingress.fqdn
output postgresqlFqdn string = postgresServer.properties.fullyQualifiedDomainName
output redisCacheHostName string = redisCache.properties.hostName
