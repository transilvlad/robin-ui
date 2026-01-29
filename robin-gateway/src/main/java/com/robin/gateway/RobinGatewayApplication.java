package com.robin.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for Robin API Gateway.
 *
 * This gateway provides unified access to Robin MTA services with:
 * - JWT authentication and authorization
 * - Rate limiting and circuit breakers
 * - Request/response caching
 * - Prometheus metrics and monitoring
 *
 * @author Robin MTA Team
 * @version 1.0.0
 */
@SpringBootApplication
public class RobinGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RobinGatewayApplication.class, args);
    }

}
