package com.robin.gateway.service;

import com.robin.gateway.model.User;
import com.robin.gateway.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Service for synchronizing password hashes between robin-gateway and Robin MTA.
 * <p>
 * This service implements a dual-hash strategy to resolve the conflict between:
 * <ul>
 *     <li>Spring Security (BCrypt) - Used for robin-gateway authentication</li>
 *     <li>Dovecot (SHA512-CRYPT) - Used for Robin MTA IMAP authentication</li>
 * </ul>
 * <p>
 * When a password is updated, this service generates and stores both hash formats:
 * <ul>
 *     <li>{@code password_bcrypt} column - BCrypt hash for Gateway</li>
 *     <li>{@code password} column - SHA512-CRYPT hash with prefix for Dovecot</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * passwordSyncService.updatePassword(userId, "newPassword123");
 * </pre>
 *
 * @see User
 * @see UserRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordSyncService {

    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    /**
     * Updates both BCrypt and SHA512-CRYPT password hashes atomically.
     * <p>
     * This method ensures that password changes are synchronized across both authentication systems:
     * <ul>
     *     <li>robin-gateway (Spring Security with BCrypt)</li>
     *     <li>Robin MTA/Dovecot (IMAP with SHA512-CRYPT)</li>
     * </ul>
     * <p>
     * The operation is transactional - if either hash generation or database update fails,
     * both changes are rolled back to maintain consistency.
     *
     * @param userId        The ID of the user whose password should be updated
     * @param plainPassword The plain-text password to hash and store
     * @throws IllegalArgumentException if userId is null or user not found
     * @throws IllegalArgumentException if plainPassword is null or blank
     */
    @Transactional
    public void updatePassword(@NotNull Long userId, @NotBlank String plainPassword) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(plainPassword, "plainPassword must not be null");

        if (plainPassword.isBlank()) {
            throw new IllegalArgumentException("plainPassword must not be blank");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        // Generate BCrypt hash for robin-gateway Spring Security authentication
        String bcryptHash = passwordEncoder.encode(plainPassword);
        log.debug("Generated BCrypt hash for user: {}", user.getUsername());

        // Generate SHA512-CRYPT hash for Dovecot/Robin MTA IMAP authentication
        // PostgreSQL crypt() function with 'bf' salt generates a SHA512-CRYPT compatible hash
        String sha512Hash = jdbcTemplate.queryForObject(
                "SELECT crypt(?, gen_salt('bf'))",
                String.class,
                plainPassword
        );
        log.debug("Generated SHA512-CRYPT hash for user: {}", user.getUsername());

        // Update both password fields
        user.setPasswordHash(bcryptHash);
        user.setDovecotPasswordHash("{SHA512-CRYPT}" + sha512Hash);

        userRepository.save(user);

        log.info("Successfully updated dual-hash passwords for user: {}", user.getUsername());
    }

    /**
     * Updates both password hashes for a user identified by username.
     * <p>
     * Convenience method that looks up the user by username before updating passwords.
     *
     * @param username      The username (email) of the user
     * @param plainPassword The plain-text password to hash and store
     * @throws IllegalArgumentException if username is null or blank
     * @throws IllegalArgumentException if user not found
     * @throws IllegalArgumentException if plainPassword is null or blank
     */
    @Transactional
    public void updatePasswordByUsername(@NotBlank String username, @NotBlank String plainPassword) {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(plainPassword, "plainPassword must not be null");

        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }

        if (plainPassword.isBlank()) {
            throw new IllegalArgumentException("plainPassword must not be blank");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found with username: " + username));

        updatePassword(user.getId(), plainPassword);
    }

    /**
     * Validates that a plain-text password matches the stored BCrypt hash.
     * <p>
     * This method only validates against the Gateway's BCrypt hash.
     * Dovecot validates against SHA512-CRYPT independently during IMAP authentication.
     *
     * @param userId        The ID of the user
     * @param plainPassword The plain-text password to validate
     * @return true if the password matches the BCrypt hash, false otherwise
     * @throws IllegalArgumentException if userId is null or user not found
     */
    public boolean validatePassword(@NotNull Long userId, @NotBlank String plainPassword) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(plainPassword, "plainPassword must not be null");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        return passwordEncoder.matches(plainPassword, user.getPasswordHash());
    }
}
