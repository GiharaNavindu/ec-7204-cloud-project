# API Gateway

This module is the edge entry point for the Cloud Auction system. It routes client traffic to the downstream services, applies authentication and authorization, enforces resilience and rate limiting, adds request correlation, exposes operational endpoints, and provides controlled fallback responses when a downstream service is unavailable.

This README documents only what is currently implemented in the codebase.

## What the Gateway Does

- Routes requests to the downstream services through Spring Cloud Gateway MVC.
- Applies JWT validation for protected endpoints.
- Enforces path-based RBAC using JWT claims.
- Retries transient downstream failures and opens circuit breakers for unavailable services.
- Returns consistent fallback responses for service failures.
- Adds request correlation IDs and structured request logs.
- Exposes Actuator health, info, metrics, and Prometheus endpoints.
- Applies configurable rate limiting, including Redis-backed distributed limiting.
- Applies configurable CORS settings from gateway properties.
- Enforces edge hardening settings such as request-size limits.

## Project Stack

- Java 21
- Spring Boot 4.0.5
- Spring Cloud Gateway Server MVC
- Spring Security-related JWT validation implemented in a custom filter
- Resilience4j for retry, circuit breaker, and time limiter configuration
- Spring Actuator and Micrometer Prometheus registry
- Spring Data Redis for distributed rate limiting

## Main Components

### Application bootstrap

- [ApiGatewayApplication](src/main/java/com/gihara/apigateway/ApiGatewayApplication.java) enables configuration properties binding for [GatewayRouteProperties](src/main/java/com/gihara/apigateway/config/GatewayRouteProperties.java).
- [ApiGatewayConfig](src/main/java/com/gihara/apigateway/config/ApiGatewayConfig.java) is a placeholder configuration class; the gateway behavior is driven by `application.yml` and filters.

### Configuration binding

The gateway configuration is centralized in [GatewayRouteProperties](src/main/java/com/gihara/apigateway/config/GatewayRouteProperties.java).

It currently binds:

- `app.gateway.public-paths`
- `app.gateway.role-rules`
- `app.gateway.rate-limit`
- `app.gateway.cors`

The properties class also provides helper methods to match public paths and rate-limit exempt paths using Ant-style patterns.

### Request filters

#### [JwtValidationFilter](src/main/java/com/gihara/apigateway/filter/JwtValidationFilter.java)

This filter is responsible for gateway authentication and authorization.

Implemented behavior:

- Skips validation for public paths defined in `app.gateway.public-paths`.
- Requires `Authorization: Bearer <token>` for protected requests.
- Validates JWT signatures using `app.jwt.secret`.
- Extracts claims from `roles`, `authorities`, `role`, and `scope`.
- Accepts roles both with and without the `ROLE_` prefix.
- Enforces path-specific RBAC rules from `app.gateway.role-rules`.
- Returns `401 Unauthorized` for missing or invalid tokens.
- Returns `403 Forbidden` for authenticated users who do not have the required role.

#### [GatewayRateLimitFilter](src/main/java/com/gihara/apigateway/filter/GatewayRateLimitFilter.java)

This filter enforces gateway-level request throttling.

Implemented behavior:

- Skips CORS preflight requests.
- Skips rate limiting when the feature is disabled.
- Skips paths listed in `app.gateway.rate-limit.exempt-paths`.
- Supports two limiter modes:
	- `memory` for in-process limiting
	- `redis` for distributed limiting using `StringRedisTemplate`
- Uses the client IP from `X-Forwarded-For`, then `X-Real-IP`, then `request.getRemoteAddr()`.
- Supports fail-open behavior when Redis is unavailable and `app.gateway.rate-limit.fail-open=true`.
- Returns `429 Too Many Requests` when the threshold is exceeded.
- Includes throttling headers:
	- `X-Rate-Limit-Limit`
	- `X-Rate-Limit-Remaining`
	- `X-Rate-Limit-Reset`
	- `Retry-After`
- Returns a JSON body with the request path, client key, limit, and window details.

#### [RequestCorrelationLoggingFilter](src/main/java/com/gihara/apigateway/filter/RequestCorrelationLoggingFilter.java)

This filter adds request correlation and structured access logging.

Implemented behavior:

- Reads `X-Request-Id` if the client sends one.
- Generates a UUID request ID when the header is missing.
- Adds the request ID to the response header.
- Puts the request ID into MDC for log correlation.
- Logs request method, path, status, and latency in milliseconds.

### CORS configuration

[GatewayCorsConfig](src/main/java/com/gihara/apigateway/config/GatewayCorsConfig.java) applies global CORS settings from `app.gateway.cors`.

Configured options include:

- allowed origin patterns
- allowed methods
- allowed headers
- exposed headers
- credential support
- max age

### Fallback controller

[GatewayFallbackController](src/main/java/com/gihara/apigateway/controller/GatewayFallbackController.java) exposes controlled fallback endpoints for downstream failures.

Implemented endpoints:

- `GET /fallback/users`
- `GET /fallback/auctions`
- `GET /fallback/bids`
- `GET /fallback/payments`
- `GET /fallback/notifications`

Each endpoint returns HTTP 503 with a JSON body containing:

- timestamp
- status
- error
- message

## Routes

The gateway routes are defined in [src/main/resources/application.yml](src/main/resources/application.yml).

### Public user routes

- `/api/users/register`
- `/api/users/login`
- `/api/users/status`

These paths are routed to `USER_SERVICE_URL` and bypass JWT validation.

### Protected routes

- `/api/users/**` except the public user endpoints above
- `/api/auctions/**`
- `/api/bids/**`
- `/api/payments/**`
- `/api/notifications/**`

### Downstream fallback targets

- user service -> `forward:/fallback/users`
- auction service -> `forward:/fallback/auctions`
- bid service -> `forward:/fallback/bids`
- payment service -> `forward:/fallback/payments`
- notification service -> `forward:/fallback/notifications`

### Retry and circuit breaker behavior

The route definitions include:

- retry filters with method and series-based retry rules
- circuit breaker filters per service
- explicit fallback URIs for all service routes that use resilience handling

## Security Model

### Authentication

The gateway expects JWTs signed with the shared secret configured in `app.jwt.secret`.

### Authorization

Role-based access is configured through `app.gateway.role-rules`.

Current role rules in the code:

- `/api/users/admin/**` -> `ADMIN`
- `/api/users/trusted/**` -> `TRUSTED_USER`, `ADMIN`
- `/api/auctions/create/**` -> `TRUSTED_USER`, `ADMIN`
- `/api/auctions/manage/**` -> `ADMIN`
- `/api/payments/refund/**` -> `ADMIN`
- `/api/notifications/admin/**` -> `ADMIN`

### Public paths

Current public paths include:

- `/api/users/register`
- `/api/users/login`
- `/api/users/status`
- `/actuator/health/**`
- `/actuator/info`
- `/fallback/**`

## Rate Limiting

Rate limiting is configured under `app.gateway.rate-limit`.

Current properties:

- `enabled`
- `mode`
- `max-requests`
- `window-seconds`
- `redis-key-prefix`
- `fail-open`
- `exempt-paths`

Implemented defaults and behavior:

- Default mode is `redis`.
- Default threshold is 60 requests per 60 seconds.
- Exempt paths include health, info, and fallback routes.
- Redis key prefix is `gateway:ratelimit:`.
- The gateway falls back to in-memory limiting if Redis cannot be used and `fail-open` is enabled.

## Observability

### Actuator

The gateway exposes:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

### Logging and metrics

- Console logs include `requestId` in the pattern.
- Metrics are tagged with `application` and `service=gateway`.
- `/actuator/info` is enabled with environment info contributions.

## Deployment and Runtime Configuration

### Environment variables used by the gateway

- `JWT_SECRET`
- `USER_SERVICE_URL`
- `AUCTION_SERVICE_URL`
- `BID_SERVICE_URL`
- `PAYMENT_SERVICE_URL`
- `NOTIFICATION_SERVICE_URL`
- `REDIS_HOST`
- `REDIS_PORT`
- `ALLOWED_ORIGIN_PATTERN`

### Docker Compose

The top-level [docker-compose.yml](../docker-compose.yml) wires the gateway to:

- `user-service`
- `redis`

It also passes the gateway service URLs and CORS origin pattern through environment variables.

### Example environment file

[.env.example](../.env.example) documents the core gateway environment variables expected for local deployment.

## Build and Run

From the `api-gateway` directory:

```powershell
.\mvnw.cmd clean test
```

```powershell
.\mvnw.cmd spring-boot:run
```

The module is configured for Java 21.

## Tests

The gateway currently includes these tests:

- [ApiGatewayApplicationTests](src/test/java/com/gihara/apigateway/ApiGatewayApplicationTests.java)
- [GatewayRateLimitFilterTest](src/test/java/com/gihara/apigateway/filter/GatewayRateLimitFilterTest.java)
- [GatewayFallbackControllerTest](src/test/java/com/gihara/apigateway/controller/GatewayFallbackControllerTest.java)
- [RequestCorrelationLoggingFilterTest](src/test/java/com/gihara/apigateway/filter/RequestCorrelationLoggingFilterTest.java)
- [GatewayStep7IntegrationTest](src/test/java/com/gihara/apigateway/GatewayStep7IntegrationTest.java)

These tests cover:

- application context startup
- in-memory and Redis-backed rate limiting
- exempt path handling
- fallback response payloads
- request ID generation and propagation
- end-to-end filter composition for the gateway edge behavior

## File Map

- [src/main/java/com/gihara/apigateway/ApiGatewayApplication.java](src/main/java/com/gihara/apigateway/ApiGatewayApplication.java)
- [src/main/java/com/gihara/apigateway/config/ApiGatewayConfig.java](src/main/java/com/gihara/apigateway/config/ApiGatewayConfig.java)
- [src/main/java/com/gihara/apigateway/config/GatewayRouteProperties.java](src/main/java/com/gihara/apigateway/config/GatewayRouteProperties.java)
- [src/main/java/com/gihara/apigateway/config/GatewayCorsConfig.java](src/main/java/com/gihara/apigateway/config/GatewayCorsConfig.java)
- [src/main/java/com/gihara/apigateway/controller/GatewayFallbackController.java](src/main/java/com/gihara/apigateway/controller/GatewayFallbackController.java)
- [src/main/java/com/gihara/apigateway/filter/JwtValidationFilter.java](src/main/java/com/gihara/apigateway/filter/JwtValidationFilter.java)
- [src/main/java/com/gihara/apigateway/filter/GatewayRateLimitFilter.java](src/main/java/com/gihara/apigateway/filter/GatewayRateLimitFilter.java)
- [src/main/java/com/gihara/apigateway/filter/RequestCorrelationLoggingFilter.java](src/main/java/com/gihara/apigateway/filter/RequestCorrelationLoggingFilter.java)
- [src/main/resources/application.yml](src/main/resources/application.yml)
- [src/test/java/com/gihara/apigateway/ApiGatewayApplicationTests.java](src/test/java/com/gihara/apigateway/ApiGatewayApplicationTests.java)
- [src/test/java/com/gihara/apigateway/GatewayStep7IntegrationTest.java](src/test/java/com/gihara/apigateway/GatewayStep7IntegrationTest.java)
- [src/test/java/com/gihara/apigateway/controller/GatewayFallbackControllerTest.java](src/test/java/com/gihara/apigateway/controller/GatewayFallbackControllerTest.java)
- [src/test/java/com/gihara/apigateway/filter/GatewayRateLimitFilterTest.java](src/test/java/com/gihara/apigateway/filter/GatewayRateLimitFilterTest.java)
- [src/test/java/com/gihara/apigateway/filter/RequestCorrelationLoggingFilterTest.java](src/test/java/com/gihara/apigateway/filter/RequestCorrelationLoggingFilterTest.java)

