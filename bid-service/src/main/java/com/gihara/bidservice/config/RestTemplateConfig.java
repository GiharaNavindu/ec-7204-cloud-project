package com.gihara.bidservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced   // tells Eureka to resolve lb://AUCTION-SERVICE to actual address
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}