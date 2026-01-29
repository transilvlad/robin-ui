package com.robin.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * OpenAPI 3.0 configuration for Robin Gateway API documentation.
 * Accessible at /swagger-ui.html and /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:robin-gateway}")
    private String applicationName;

    @Bean
    public OpenAPI robinGatewayOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .externalDocs(externalDocumentation())
                .servers(Arrays.asList(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("https://api.robin-mta.example.com").description("Production")
                ))
                .components(securityComponents())
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(errorSchemas());
    }

    private Info apiInfo() {
        return new Info()
                .title("Robin MTA Gateway API")
                .description("""
                        ## Robin API Gateway

                        Unified API gateway for Robin MTA (Mail Transfer Agent) management interface.

                        ### Features
                        - **JWT Authentication**: Secure token-based authentication with refresh tokens
                        - **Role-Based Access Control (RBAC)**: Admin, User, ReadOnly, Operator roles
                        - **Rate Limiting**: Redis-backed rate limiting (100 req/min default)
                        - **Circuit Breakers**: Resilience4j protection for backend services
                        - **Proxy Routing**: Transparent routing to Robin Client API (8090) and Service API (8080)
                        - **Domain Management**: Email domain and alias CRUD operations
                        - **Health Monitoring**: Aggregated health checks across all services

                        ### Authentication
                        1. Login with credentials at `/api/v1/auth/login`
                        2. Receive access token (30 min) and refresh token (7 days, HttpOnly cookie)
                        3. Use access token in `Authorization: Bearer <token>` header
                        4. Refresh token automatically via `/api/v1/auth/refresh`

                        ### Default Credentials (Development)
                        - **Username**: admin@robin.local
                        - **Password**: admin123

                        ⚠️ **Change credentials in production!**

                        ### Rate Limits
                        - Login: 5 req/min per IP
                        - Queue operations: 100 req/min per user
                        - Global: 100 req/min per IP

                        ### Error Handling
                        All endpoints return consistent error responses with HTTP status codes:
                        - `400` Bad Request - Invalid input
                        - `401` Unauthorized - Authentication required
                        - `403` Forbidden - Insufficient permissions
                        - `404` Not Found - Resource not found
                        - `429` Too Many Requests - Rate limit exceeded
                        - `500` Internal Server Error - Server error
                        - `503` Service Unavailable - Backend service down
                        """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("Robin MTA Team")
                        .email("support@robin-mta.example.com")
                        .url("https://github.com/transilvlad/robin"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0.html"));
    }

    private ExternalDocumentation externalDocumentation() {
        return new ExternalDocumentation()
                .description("Robin MTA Documentation")
                .url("https://github.com/transilvlad/robin/wiki");
    }

    private Components securityComponents() {
        return new Components()
                .addSecuritySchemes("Bearer Authentication",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("""
                                        JWT Bearer token authentication.

                                        **How to obtain a token:**
                                        1. Call `POST /api/v1/auth/login` with credentials
                                        2. Extract `accessToken` from response
                                        3. Use token in Authorization header: `Bearer <token>`

                                        **Token Expiration:**
                                        - Access Token: 30 minutes
                                        - Refresh Token: 7 days (HttpOnly cookie)

                                        **Example:**
                                        ```
                                        Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
                                        ```
                                        """)
                );
    }

    private Components errorSchemas() {
        Map<String, Schema> schemas = new HashMap<>();

        // Error Response Schema
        Schema<?> errorSchema = new Schema<>()
                .type("object")
                .description("Standard error response")
                .addProperty("timestamp", new Schema<>().type("string").format("date-time").example("2026-01-27T12:00:00Z"))
                .addProperty("status", new Schema<>().type("integer").example(400))
                .addProperty("error", new Schema<>().type("string").example("Bad Request"))
                .addProperty("message", new Schema<>().type("string").example("Invalid request parameters"))
                .addProperty("path", new Schema<>().type("string").example("/api/v1/domains"));

        schemas.put("ErrorResponse", errorSchema);

        // Validation Error Schema
        Schema<?> validationErrorSchema = new Schema<>()
                .type("object")
                .description("Validation error response")
                .addProperty("timestamp", new Schema<>().type("string").format("date-time"))
                .addProperty("status", new Schema<>().type("integer").example(400))
                .addProperty("error", new Schema<>().type("string").example("Validation Failed"))
                .addProperty("message", new Schema<>().type("string").example("Input validation failed"))
                .addProperty("errors", new Schema<>()
                        .type("array")
                        .items(new Schema<>()
                                .type("object")
                                .addProperty("field", new Schema<>().type("string").example("email"))
                                .addProperty("message", new Schema<>().type("string").example("Invalid email format"))
                        )
                );

        schemas.put("ValidationErrorResponse", validationErrorSchema);

        // Common API Responses
        Components components = new Components();
        components.setSchemas(schemas);

        // Add standard error responses
        components.addResponses("BadRequest", new ApiResponse()
                .description("Bad Request - Invalid input parameters")
                .content(new Content()
                        .addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));

        components.addResponses("Unauthorized", new ApiResponse()
                .description("Unauthorized - Authentication required or token expired")
                .content(new Content()
                        .addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));

        components.addResponses("Forbidden", new ApiResponse()
                .description("Forbidden - Insufficient permissions")
                .content(new Content()
                        .addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));

        components.addResponses("NotFound", new ApiResponse()
                .description("Not Found - Resource does not exist")
                .content(new Content()
                        .addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));

        components.addResponses("TooManyRequests", new ApiResponse()
                .description("Too Many Requests - Rate limit exceeded")
                .content(new Content()
                        .addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));

        components.addResponses("InternalServerError", new ApiResponse()
                .description("Internal Server Error - Unexpected server error")
                .content(new Content()
                        .addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));

        components.addResponses("ServiceUnavailable", new ApiResponse()
                .description("Service Unavailable - Backend service is down or circuit breaker is open")
                .content(new Content()
                        .addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));

        return components;
    }
}
