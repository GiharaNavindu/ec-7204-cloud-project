# API Gateway Observability

This project includes a live observability stack for the API Gateway.

## Services

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Folder: `http://localhost:3000/dashboards/f/cfk9c74p88poge/Cloud%20Auction`
- Prometheus target page: `http://localhost:9090/targets`
- API Gateway metrics: `http://localhost:8080/actuator/prometheus`

## Login

Grafana is provisioned with these default credentials:

- username: `admin`
- password: `admin`

## What is included

- Prometheus scrapes the gateway metrics endpoint.
- Grafana automatically loads the API Gateway dashboard.
- Prometheus alert rules are loaded from the repository.

## How to run

From the repository root:

```powershell
docker compose up -d --build
```

Then open Grafana and view the `Cloud Auction` folder, which contains the API Gateway dashboard.
