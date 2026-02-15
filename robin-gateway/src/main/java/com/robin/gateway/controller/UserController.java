package com.robin.gateway.controller;

import com.robin.gateway.model.User;
import com.robin.gateway.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for managing users, roles, and permissions")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users", description = "Returns a list of all registered users with sensitive data sanitized")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved users",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = User.class)))),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required", content = @Content)
    })
    public Flux<User> listUsers() {
        return userService.getAllUsers()
                .map(this::sanitizeUser);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new user", description = "Creates a new user account with specified roles and permissions")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = User.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required", content = @Content)
    })
    public Mono<ResponseEntity<User>> createUser(@Valid @RequestBody User user) {
        return userService.createUser(user)
                .map(this::sanitizeUser)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user", description = "Updates an existing user's details, roles, or status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = User.class))),
        @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required", content = @Content)
    })
    public Mono<ResponseEntity<User>> updateUser(
            @Parameter(description = "Username of the user to update") @PathVariable String username,
            @Valid @RequestBody User user) {
        return userService.updateUser(username, user)
                .map(this::sanitizeUser)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete user", description = "Permanently removes a user account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User deleted successfully", content = @Content),
        @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required", content = @Content)
    })
    public Mono<ResponseEntity<Void>> deleteUser(
            @Parameter(description = "Username of the user to delete") @PathVariable String username) {
        return userService.deleteUser(username)
                .map(v -> ResponseEntity.ok().<Void>build());
    }

    private User sanitizeUser(User user) {
        // Don't return password hashes in API responses
        user.setPasswordHash(null);
        user.setDovecotPasswordHash(null);
        return user;
    }
}
