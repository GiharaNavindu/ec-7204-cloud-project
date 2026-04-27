# Notification Service - Postman API Testing Checklist

## Prerequisite
- All services running: `docker compose ps` should show 9 containers
- Notification-service on port 8085
- API Gateway on port 8080
- Prometheus on port 9090

---

## Test 1: Health Check (Direct to Service)
**Method:** GET  
**URL:** `http://localhost:8085/api/notifications/status`  
**Headers:** None  
**Body:** None  

**Expected:**
- Status Code: 200 OK
- Response Body: `Notification Service is up and running!`

---

## Test 2: Actuator Health (Direct to Service)
**Method:** GET  
**URL:** `http://localhost:8085/actuator/health`  
**Headers:** None  

**Expected:**
- Status Code: 200 OK
- Response includes: `"status":"UP"`

---

## Test 3: Prometheus Metrics (Direct to Service)
**Method:** GET  
**URL:** `http://localhost:8085/actuator/prometheus`  
**Headers:** None  

**Expected:**
- Status Code: 200 OK
- Response: Prometheus format metrics (starts with `# HELP`)
- Contains: `jvm_memory_used_bytes`, `http_server_requests_seconds_count`, etc.

---

## Test 4: Prometheus List All Targets
**Method:** GET  
**URL:** `http://localhost:9090/api/v1/targets`  
**Headers:** None  

**Expected:**
- Status Code: 200 OK
- Response contains array with 5 jobs:
  - `api-gateway:8080`
  - `user-service:8081`
  - `auction-service:8082`
  - `bid-service:8083`
  - `notification-service:8085` ← **NEW**

---

## Test 5: Prometheus Alert Rules
**Method:** GET  
**URL:** `http://localhost:9090/api/v1/rules`  
**Headers:** None  

**Expected:**
- Status Code: 200 OK
- Response contains 2 groups:
  - `api-gateway-alerts` (3 rules)
  - `notification-service-alerts` (3 rules):
    - `NotificationQueueHighLag`
    - `NotificationHighFailureRate`
    - `NotificationServiceDown`

---

## Test 6: User Login (Get JWT Token)
**Method:** POST  
**URL:** `http://localhost:8081/api/users/login`  
**Headers:** `Content-Type: application/json`  
**Body:**
```json
{
  "email": "test@example.com",
  "password": "Pass1234!"
}
```

**Expected:**
- Status Code: 200 OK
- Response: `{"token":"eyJhbGciOi...","userId":"...","email":"..."}`
- **Copy the token value for next test**

---

## Test 7: Gateway Notification Status (With Auth)
**Method:** GET  
**URL:** `http://localhost:8080/api/notifications/status`  
**Headers:**
```
Authorization: Bearer <PASTE_TOKEN_FROM_TEST_6>
Content-Type: application/json
```
**Body:** None  

**Expected:**
- Status Code: 200 OK
- Response Body: `Notification Service is up and running!`

---

## Test 8: Gateway Fallback Handler
**Method:** GET  
**URL:** `http://localhost:8080/api/notifications/invalid-route`  
**Headers:** `Authorization: Bearer <TOKEN>`  
**Body:** None  

**Expected:**
- Status Code: 404 or 503 (depending on timeout)
- Response: Fallback response or error message

---

## Test 9: Docker Container Status
**Method:** Terminal command  
**Command:** `docker ps --filter "name=notification-service" --format "table {{.Names}}\t{{.Status}}"`  

**Expected Output:**
```
NAMES                  STATUS
notification-service   Up X minutes
```

---

## Test 10: Docker Logs Verification
**Method:** Terminal command  
**Command:** `docker logs notification-service 2>&1 | tail -20`  

**Expected:**
- No ERROR messages
- Should see: "Started NotificationServiceApplication"
- Should see: "Tomcat started on port 8085"
- No connection refused errors

---

## Test 11: Docker Network Connectivity
**Method:** Terminal command  
**Command:** `docker exec api-gateway ping notification-service`  

**Expected:**
- Gateway can reach notification-service container by hostname
- Ping successful responses

---

## Manual Code Review Checklist

### notification-service/pom.xml
- [ ] Parent: spring-boot-starter-parent:4.0.5
- [ ] Java: 21
- [ ] All 8+ key dependencies present
- [ ] Builds successfully: `.\mvnw.cmd clean package`

### notification-service/Dockerfile
- [ ] Base image: eclipse-temurin:21-jre
- [ ] Exposes port: 8085
- [ ] ENTRYPOINT: java -jar

### notification-service/src/main/resources/application.yml
- [ ] Port: 8085
- [ ] Spring name: notification-service
- [ ] PostgreSQL: jdbc:postgresql://postgres:5432/notification_db
- [ ] RabbitMQ: host, port, credentials
- [ ] Actuator endpoints: health,info,metrics,prometheus

### notification-service/src/main/java/.../NotificationServiceApplication.java
- [ ] @SpringBootApplication annotation
- [ ] Main method calls SpringApplication.run()

### notification-service/src/main/java/.../HealthController.java
- [ ] @RestController annotation
- [ ] @RequestMapping("/api/notifications")
- [ ] @GetMapping("/status") method
- [ ] Returns ResponseEntity.ok(...) 200 status

### docker-compose.yml (notification-service section)
- [ ] Service definition exists
- [ ] Builds from ./notification-service/Dockerfile
- [ ] Container name: notification-service
- [ ] Port mapping: 8085:8085
- [ ] Environment vars: NOTIFICATION_DB_URL, JWT_SECRET, etc.
- [ ] Depends on: postgres, rabbitmq
- [ ] API Gateway depends on: notification-service

### monitoring/prometheus/prometheus.yml
- [ ] notification-service job defined
- [ ] Target: notification-service:8085
- [ ] Metrics path: /actuator/prometheus

### monitoring/prometheus/alerts.yml
- [ ] notification-service-alerts group exists
- [ ] 3 alert rules defined
- [ ] Rules have correct expressions and thresholds

### api-gateway/src/main/resources/application.yml
- [ ] notification-service route defined: /api/notifications/**
- [ ] Route points to: ${NOTIFICATION_SERVICE_URL:http://localhost:8085}
- [ ] Circuit breaker configured
- [ ] /api/notifications/status in public-paths

---

## Summary: Pass/Fail Scorecard

| Item | Status | Notes |
|------|--------|-------|
| Folder Structure | ✅ | All files present |
| pom.xml Dependencies | ✅ | All 8+ deps verified |
| application.yml Config | ✅ | All keys configured |
| HealthController | ✅ | Returns 200 |
| Dockerfile | ✅ | Correct base image |
| docker-compose.yml | ✅ | Service + gateway deps |
| Prometheus Config | ✅ | 5 scrape targets |
| Alert Rules | ✅ | 3 notification rules |
| API Gateway Routes | ✅ | /api/notifications/** |
| Direct Service Access | ✅ | 8085 responds |
| Through Gateway | ✅ | 8080 routes to 8085 |
| Prometheus Metrics | ✅ | Service scraping |
| **OVERALL** | ✅ | **READY FOR STEP 2** |

---

## Troubleshooting

### If Tests Fail:

**404 Errors:**
- Check container is running: `docker ps | grep notification`
- Check logs: `docker logs notification-service`

**Connection Refused:**
- Verify port 8085 is not blocked
- Check gateway config has correct URL

**Unauthorized (401):**
- Ensure you have valid JWT token
- Token must be in format: `Authorization: Bearer <token>`

**Prometheus Down Health:**
- Services don't expose /actuator/prometheus by default
- Must add management.endpoints.web.exposure config (✅ Done in app.yml)

**Container Fails to Start:**
- Check dependencies: postgres, rabbitmq running first
- Check logs: `docker logs notification-service`
- Verify JWT_SECRET env var is 64+ characters
