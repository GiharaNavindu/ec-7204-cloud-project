# 🚀 Ruhuna Auction System - Deployment Guide

Welcome to the Ruhuna Auction System! This document provides a comprehensive guide to understanding, configuring, and deploying the application from scratch. Whether you are a developer looking to run it locally or an operations engineer deploying it to the cloud, this guide has you covered.

---

## 🏗 System Architecture

The Ruhuna Auction System is built on a **Microservices Architecture** using Spring Boot and Java. It consists of the following components:

### Core Services
*   **API Gateway (`api-gateway`)**: The central entry point. Handles request routing, JWT-based authentication, rate limiting (Redis), and serves the frontend static files.
*   **User Service (`user-service`)**: Manages user accounts, authentication (Local + Google OAuth2), and authorization.
*   **Auction Service (`auction-service`)**: Core logic for creating and managing auction listings.
*   **Bid Service (`bid-service`)**: Handles the high-frequency bidding process and real-time updates.
*   **Notification Service (`notification-service`)**: Dispatches alerts and notifications to users via RabbitMQ.

### Infrastructure & Storage
*   **PostgreSQL**: Primary relational database (separate databases for each service: `user_db`, `auction_db`, `bid_db`, `notification_db`).
*   **Redis**: Used by the API Gateway for rate limiting and session management.
*   **RabbitMQ**: Message broker for asynchronous communication between services (especially for notifications).
*   **Zipkin**: Distributed tracing to monitor request flow across microservices.
*   **Prometheus & Grafana**: Monitoring stack for metrics collection and visualization.

---

## 📋 Prerequisites

Before you begin, ensure you have the following installed:

*   **Docker & Docker Compose**: (Recommended) For easy local orchestration.
*   **Java 17 SDK**: If you plan to build the services manually.
*   **Maven**: For dependency management and building `.jar` files.
*   **Azure CLI**: If you are deploying to Azure.
*   **PowerShell**: Required for running the Azure bootstrap scripts.

---

## 💻 Local Deployment (Quick Start)

The easiest way to get the system running locally is using Docker Compose.

### 1. Configure Environment Variables
Create a `.env` file in the root directory. You can use the following template (refer to `.env.example` if available):

```env
# Database Credentials
POSTGRES_USER=admin
POSTGRES_PASSWORD=your_secure_password

# JWT Security
JWT_SECRET=your_super_secret_jwt_key_at_least_64_characters
JWT_EXPIRATION=86400000

# Google OAuth (Optional for local testing)
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret

# Service URLs (Defaults for Docker)
USER_DB_URL=jdbc:postgresql://postgres:5432/user_db
AUCTION_DB_URL=jdbc:postgresql://postgres:5432/auction_db
BID_DB_URL=jdbc:postgresql://postgres:5432/bid_db
NOTIFICATION_DB_URL=jdbc:postgresql://postgres:5432/notification_db

# Monitoring
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=admin_password
```

### 2. Launch the System
Run the following command in the root directory:

```bash
docker-compose up --build
```

This will:
1.  Initialize the PostgreSQL databases using `init.sql`.
2.  Start all infrastructure (Postgres, Redis, RabbitMQ, Zipkin, Prometheus, Grafana).
3.  Build and start all microservices.

### 3. Access the Application
*   **Web UI**: [http://localhost:8080](http://localhost:8080)
*   **API Gateway**: [http://localhost:8080/api/...](http://localhost:8080/api/...)
*   **Grafana Dashboards**: [http://localhost:3000](http://localhost:3000) (Default login: `admin`/`admin_password`)
*   **Prometheus**: [http://localhost:9090](http://localhost:9090)
*   **RabbitMQ Management**: [http://localhost:15672](http://localhost:15672) (Default: `guest`/`guest`)

---

## ☁️ Cloud Deployment (Azure)

The project includes an automated "Bootstrap" script to deploy the entire infrastructure to Azure Container Apps.

### 1. Login to Azure
```powershell
az login
```

### 2. Run the Bootstrap Script
Execute the `bootstrap-azure.ps1` script. This script is interactive and will prompt you for necessary secrets if not provided.

```powershell
./bootstrap-azure.ps1 -ResourceGroupName "my-auction-rg" -Location "eastus" -AcrName "myauctionregistry"
```

**What this script does:**
1.  **Creates a Resource Group** in your specified location.
2.  **Sets up Azure Container Registry (ACR)** to store your Docker images.
3.  **Provisions Azure Database for PostgreSQL (Flexible Server)** and creates the required databases.
4.  **Provisions Azure Cache for Redis**.
5.  **Builds & Pushes** all microservice Docker images to ACR.
6.  **Deploys Azure Container Apps** using the Bicep template (`infra/container-apps.bicep`).
7.  **Generates GitHub Secrets**: Outputs the credentials you need to set up CI/CD.

### 3. CI/CD with GitHub Actions
Once bootstrapped, you can use the outputted credentials to populate your GitHub Repository Secrets:
*   `AZURE_CREDENTIALS`: The JSON output for the Service Principal.
*   `ACR_LOGIN_SERVER`, `ACR_USERNAME`, `ACR_PASSWORD`: Registry credentials.
*   `AZURE_RESOURCE_GROUP`: The name of your resource group.

The workflows in `.github/workflows/` will then automatically deploy updates whenever you push to the `main` branch.

### 4. Manual Cloud Builds (Azure)
If you need to build and push a specific service manually to Azure Container Registry (without using the bootstrap script), **always run the command from the project root**:

```powershell
# Example for User Service
az acr build --registry <your_registry_name> --image user-service:latest -f user-service/Dockerfile .
```
> **Note:** The `.` at the end is crucial. It sets the project root as the build context, which the Dockerfiles expect.

---

## 📊 Monitoring & Observability

The system is designed with observability in mind:

*   **Health Checks**: Each service exposes a health endpoint at `/actuator/health`.
*   **Metrics**: Prometheus scrapes metrics from all services via the API Gateway.
*   **Dashboards**: Pre-configured Grafana dashboards are located in `monitoring/grafana/dashboards`. These are automatically imported during local deployment.
*   **Tracing**: Request traces are sent to Zipkin. In production (Azure), you can point `ZIPKIN_URL` to your distributed tracing backend.

---

## 🛠 Troubleshooting

*   **Database Connection Issues**: Ensure the `POSTGRES_PASSWORD` in your `.env` matches the one used during the first run of the Postgres container. If you change it later, you may need to delete the `postgres_data` volume.
*   **RabbitMQ Startup**: The `bid-service` and `notification-service` depend on RabbitMQ. If they fail to start, wait a few seconds and restart them (or let Docker Compose's `restart: on-failure` handle it).
*   **JWT Errors**: Ensure your `JWT_SECRET` is long enough (at least 64 chars recommended) and consistent across all services.
*   **Azure Deployment Failures**: Check the "Activity Log" in the Azure Portal for the Resource Group. Common issues include quota limits for "Burstable" VM sizes in certain regions.

---

**Developed with ❤️ for the Ruhuna Auction System.**
