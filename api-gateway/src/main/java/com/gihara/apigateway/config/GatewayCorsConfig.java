package com.gihara.apigateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GatewayCorsConfig implements WebMvcConfigurer {

    private final GatewayRouteProperties routeProperties;

    public GatewayCorsConfig(GatewayRouteProperties routeProperties) {
        this.routeProperties = routeProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        GatewayRouteProperties.Cors cors = routeProperties.getCors();
        registry.addMapping("/**")
                .allowedOriginPatterns(cors.getAllowedOriginPatterns().toArray(String[]::new))
                .allowedMethods(cors.getAllowedMethods().toArray(String[]::new))
                .allowedHeaders(cors.getAllowedHeaders().toArray(String[]::new))
                .exposedHeaders(cors.getExposedHeaders().toArray(String[]::new))
                .allowCredentials(cors.isAllowCredentials())
                .maxAge(cors.getMaxAgeSeconds());
    }
}