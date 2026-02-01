package com.robin.gateway.controller;

import com.robin.gateway.model.User;
import com.robin.gateway.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<User> listUsers() {
        return userService.getAllUsers()
                .map(this::sanitizeUser);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<User>> createUser(@RequestBody User user) {
        return userService.createUser(user)
                .map(this::sanitizeUser)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<User>> updateUser(@PathVariable String username, @RequestBody User user) {
        return userService.updateUser(username, user)
                .map(this::sanitizeUser)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable String username) {
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
