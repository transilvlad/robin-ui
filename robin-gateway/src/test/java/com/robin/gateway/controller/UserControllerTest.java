package com.robin.gateway.controller;

import com.robin.gateway.model.User;
import com.robin.gateway.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("UserController Tests")
class UserControllerTest {

    @Mock
    private UserService userService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        UserController controller = new UserController(userService);

        // Bind to controller without security (testing controller logic only)
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/users should return list of users with sanitized passwords")
    void testListUsers() {
        // Given
        User user1 = User.builder()
            .id(1L)
            .username("user1@test.com")
            .passwordHash("$2a$12$hashedPassword1")
            .dovecotPasswordHash("{SHA512-CRYPT}$6$...")
            .roles(Set.of("ROLE_USER"))
            .enabled(true)
            .build();

        User user2 = User.builder()
            .id(2L)
            .username("admin@test.com")
            .passwordHash("$2a$12$hashedPassword2")
            .dovecotPasswordHash("{SHA512-CRYPT}$6$...")
            .roles(Set.of("ROLE_ADMIN"))
            .enabled(true)
            .build();

        when(userService.getAllUsers())
            .thenReturn(Flux.just(user1, user2));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/users")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].username").isEqualTo("user1@test.com")
            .jsonPath("$[0].passwordHash").doesNotExist()
            .jsonPath("$[0].dovecotPasswordHash").doesNotExist()
            .jsonPath("$[0].roles[0]").isEqualTo("ROLE_USER")
            .jsonPath("$[1].username").isEqualTo("admin@test.com")
            .jsonPath("$[1].passwordHash").doesNotExist()
            .jsonPath("$[1].dovecotPasswordHash").doesNotExist()
            .jsonPath("$[1].roles[0]").isEqualTo("ROLE_ADMIN");

        verify(userService).getAllUsers();
    }

    @Test
    @DisplayName("GET /api/v1/users should handle empty user list")
    void testListUsersEmpty() {
        // Given
        when(userService.getAllUsers())
            .thenReturn(Flux.empty());

        // When & Then
        webTestClient.get()
            .uri("/api/v1/users")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(0);

        verify(userService).getAllUsers();
    }

    @Test
    @DisplayName("POST /api/v1/users should create user and return sanitized response")
    void testCreateUser() {
        // Given
        User requestUser = User.builder()
            .username("newuser@test.com")
            .passwordHash("$2a$12$hashedPassword")
            .roles(Set.of("ROLE_USER"))
            .build();

        User createdUser = User.builder()
            .id(10L)
            .username("newuser@test.com")
            .passwordHash("$2a$12$hashedPassword")
            .dovecotPasswordHash("{SHA512-CRYPT}$6$...")
            .roles(Set.of("ROLE_USER"))
            .enabled(true)
            .build();

        when(userService.createUser(any(User.class)))
            .thenReturn(Mono.just(createdUser));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/users")
            .bodyValue(requestUser)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(10)
            .jsonPath("$.username").isEqualTo("newuser@test.com")
            .jsonPath("$.passwordHash").doesNotExist()
            .jsonPath("$.dovecotPasswordHash").doesNotExist()
            .jsonPath("$.enabled").isEqualTo(true);

        verify(userService).createUser(any(User.class));
    }

    @Test
    @DisplayName("POST /api/v1/users should handle service error")
    void testCreateUserServiceError() {
        // Given
        User requestUser = User.builder()
            .username("duplicate@test.com")
            .passwordHash("$2a$12$hashedPassword")
            .build();

        when(userService.createUser(any(User.class)))
            .thenReturn(Mono.error(new IllegalArgumentException("Username already exists")));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/users")
            .bodyValue(requestUser)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(userService).createUser(any(User.class));
    }

    @Test
    @DisplayName("POST /api/v1/users should create admin user with multiple roles")
    void testCreateAdminUser() {
        // Given
        User requestUser = User.builder()
            .username("admin@test.com")
            .passwordHash("$2a$12$hashedPassword")
            .roles(Set.of("ROLE_ADMIN", "ROLE_USER"))
            .build();

        User createdUser = User.builder()
            .id(20L)
            .username("admin@test.com")
            .passwordHash("$2a$12$hashedPassword")
            .dovecotPasswordHash("{SHA512-CRYPT}$6$...")
            .roles(Set.of("ROLE_ADMIN", "ROLE_USER"))
            .enabled(true)
            .build();

        when(userService.createUser(any(User.class)))
            .thenReturn(Mono.just(createdUser));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/users")
            .bodyValue(requestUser)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(20)
            .jsonPath("$.username").isEqualTo("admin@test.com")
            .jsonPath("$.passwordHash").doesNotExist()
            .jsonPath("$.dovecotPasswordHash").doesNotExist()
            .jsonPath("$.roles").isArray()
            .jsonPath("$.roles.length()").isEqualTo(2);

        verify(userService).createUser(any(User.class));
    }

    @Test
    @DisplayName("PUT /api/v1/users/{username} should update user and return sanitized response")
    void testUpdateUser() {
        // Given
        String username = "user@test.com";

        User updateRequest = User.builder()
            .username(username)
            .passwordHash("dummyHash")
            .roles(Set.of("ROLE_ADMIN"))
            .enabled(true)
            .build();

        User updatedUser = User.builder()
            .id(1L)
            .username(username)
            .passwordHash("$2a$12$hashedPassword")
            .dovecotPasswordHash("{SHA512-CRYPT}$6$...")
            .roles(Set.of("ROLE_ADMIN"))
            .enabled(true)
            .build();

        when(userService.updateUser(eq(username), any(User.class)))
            .thenReturn(Mono.just(updatedUser));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/users/{username}", username)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.username").isEqualTo(username)
            .jsonPath("$.passwordHash").doesNotExist()
            .jsonPath("$.dovecotPasswordHash").doesNotExist()
            .jsonPath("$.roles[0]").isEqualTo("ROLE_ADMIN")
            .jsonPath("$.enabled").isEqualTo(true);

        verify(userService).updateUser(eq(username), any(User.class));
    }

    @Test
    @DisplayName("PUT /api/v1/users/{username} should handle user not found")
    void testUpdateUserNotFound() {
        // Given
        String username = "nonexistent@test.com";

        User updateRequest = User.builder()
            .username(username)
            .passwordHash("dummyHash")
            .enabled(false)
            .build();

        when(userService.updateUser(eq(username), any(User.class)))
            .thenReturn(Mono.error(new RuntimeException("User not found: " + username)));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/users/{username}", username)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(userService).updateUser(eq(username), any(User.class));
    }

    @Test
    @DisplayName("PUT /api/v1/users/{username} should update user quota")
    void testUpdateUserQuota() {
        // Given
        String username = "user@test.com";

        User updateRequest = User.builder()
            .username(username)
            .passwordHash("dummyHash")
            .quotaBytes(5000000000L) // 5GB
            .build();

        User updatedUser = User.builder()
            .id(1L)
            .username(username)
            .passwordHash("$2a$12$hashedPassword")
            .quotaBytes(5000000000L)
            .roles(Set.of("ROLE_USER"))
            .enabled(true)
            .build();

        when(userService.updateUser(eq(username), any(User.class)))
            .thenReturn(Mono.just(updatedUser));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/users/{username}", username)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.quotaBytes").isEqualTo(5000000000L)
            .jsonPath("$.passwordHash").doesNotExist();

        verify(userService).updateUser(eq(username), any(User.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{username} should delete user successfully")
    void testDeleteUser() {
        // Given
        String username = "user@test.com";

        when(userService.deleteUser(username))
            .thenReturn(Mono.empty());

        // When & Then
        webTestClient.delete()
            .uri("/api/v1/users/{username}", username)
            .exchange()
            .expectStatus().isOk();

        verify(userService).deleteUser(username);
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{username} should handle user not found")
    void testDeleteUserNotFound() {
        // Given
        String username = "nonexistent@test.com";

        when(userService.deleteUser(username))
            .thenReturn(Mono.error(new RuntimeException("User not found: " + username)));

        // When & Then
        webTestClient.delete()
            .uri("/api/v1/users/{username}", username)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(userService).deleteUser(username);
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{username} should handle delete constraint violation")
    void testDeleteUserConstraintViolation() {
        // Given
        String username = "admin@test.com";

        when(userService.deleteUser(username))
            .thenReturn(Mono.error(new IllegalStateException("Cannot delete last admin user")));

        // When & Then
        webTestClient.delete()
            .uri("/api/v1/users/{username}", username)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(userService).deleteUser(username);
    }

    @Test
    @DisplayName("GET /api/v1/users should handle service error")
    void testListUsersServiceError() {
        // Given
        when(userService.getAllUsers())
            .thenReturn(Flux.error(new RuntimeException("Database connection failed")));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/users")
            .exchange()
            .expectStatus().is5xxServerError();

        verify(userService).getAllUsers();
    }

    @Test
    @DisplayName("POST /api/v1/users should create user with permissions")
    void testCreateUserWithPermissions() {
        // Given
        User requestUser = User.builder()
            .username("user@test.com")
            .passwordHash("$2a$12$hashedPassword")
            .roles(Set.of("ROLE_USER"))
            .permissions(Set.of("READ_DOMAINS", "WRITE_DOMAINS"))
            .build();

        User createdUser = User.builder()
            .id(30L)
            .username("user@test.com")
            .passwordHash("$2a$12$hashedPassword")
            .dovecotPasswordHash("{SHA512-CRYPT}$6$...")
            .roles(Set.of("ROLE_USER"))
            .permissions(Set.of("READ_DOMAINS", "WRITE_DOMAINS"))
            .enabled(true)
            .build();

        when(userService.createUser(any(User.class)))
            .thenReturn(Mono.just(createdUser));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/users")
            .bodyValue(requestUser)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(30)
            .jsonPath("$.permissions").isArray()
            .jsonPath("$.permissions.length()").isEqualTo(2)
            .jsonPath("$.passwordHash").doesNotExist()
            .jsonPath("$.dovecotPasswordHash").doesNotExist();

        verify(userService).createUser(any(User.class));
    }

    @Test
    @DisplayName("PUT /api/v1/users/{username} should disable user account")
    void testDisableUserAccount() {
        // Given
        String username = "user@test.com";

        User updateRequest = User.builder()
            .username(username)
            .passwordHash("dummyHash")
            .enabled(false)
            .build();

        User updatedUser = User.builder()
            .id(1L)
            .username(username)
            .passwordHash("$2a$12$hashedPassword")
            .roles(Set.of("ROLE_USER"))
            .enabled(false)
            .build();

        when(userService.updateUser(eq(username), any(User.class)))
            .thenReturn(Mono.just(updatedUser));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/users/{username}", username)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(false)
            .jsonPath("$.passwordHash").doesNotExist();

        verify(userService).updateUser(eq(username), any(User.class));
    }
}
