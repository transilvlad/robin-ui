package com.robin.gateway.service;

import com.robin.gateway.model.User;
import com.robin.gateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordSyncService passwordSyncService;

    public Flux<User> getAllUsers() {
        return Mono.fromCallable(userRepository::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<User> getUser(String username) {
        return Mono.fromCallable(() -> userRepository.findByUsername(username))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty));
    }

    public Mono<User> createUser(User user) {
        return Mono.fromCallable(() -> {
            if (userRepository.existsByUsername(user.getUsername())) {
                throw new IllegalArgumentException("Username already exists");
            }

            // Extract plain password before saving
            String plainPassword = user.getPasswordHash();

            // Save user first (password fields will be null initially)
            user.setPasswordHash(null);
            user.setDovecotPasswordHash(null);
            User savedUser = userRepository.save(user);

            // Use PasswordSyncService to set both BCrypt and SHA512-CRYPT hashes
            passwordSyncService.updatePassword(savedUser.getId(), plainPassword);

            // Reload user with updated password hashes
            return userRepository.findById(savedUser.getId())
                    .orElseThrow(() -> new IllegalStateException("User not found after creation"));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<User> updateUser(String username, User updated) {
        return Mono.fromCallable(() -> {
            User existing = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Update password using PasswordSyncService for dual-hash strategy
            if (updated.getPasswordHash() != null && !updated.getPasswordHash().isEmpty()) {
                passwordSyncService.updatePassword(existing.getId(), updated.getPasswordHash());
                // Reload to get updated password hashes
                existing = userRepository.findById(existing.getId())
                        .orElseThrow(() -> new IllegalStateException("User not found after password update"));
            }

            // Update other fields
            if (updated.getQuotaBytes() != null) {
                existing.setQuotaBytes(updated.getQuotaBytes());
            }
            if (updated.getEnabled() != null) {
                existing.setEnabled(updated.getEnabled());
            }
            if (updated.getRoles() != null && !updated.getRoles().isEmpty()) {
                existing.setRoles(updated.getRoles());
            }
            if (updated.getPermissions() != null && !updated.getPermissions().isEmpty()) {
                existing.setPermissions(updated.getPermissions());
            }

            return userRepository.save(existing);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> deleteUser(String username) {
        return Mono.fromRunnable(() -> {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            userRepository.delete(user);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
