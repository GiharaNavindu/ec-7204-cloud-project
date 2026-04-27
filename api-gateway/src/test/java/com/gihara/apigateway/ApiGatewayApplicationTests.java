package com.gihara.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "app.jwt.secret=test_secret_for_api_gateway_jwt_needs_to_be_at_least_sixty_four_chars_long_12345"
})
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }

}
