package com.robin.gateway.service;

import com.robin.gateway.model.User;
import com.robin.gateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PasswordSyncService.
 * <p>
 * Tests the dual-hash password strategy that maintains synchronization between:
 * <ul>
 *     <li>Spring Security BCrypt authentication (robin-gateway)</li>
 *     <li>Dovecot SHA512-CRYPT authentication (Robin MTA)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordSyncService Tests")
class PasswordSyncServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PasswordSyncService passwordSyncService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("test@robin.local")
                .passwordHash("$2a$12$oldBCryptHash")
                .dovecotPasswordHash("{SHA512-CRYPT}$6$oldSHA512Hash")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }

    @Test
    @DisplayName("should update both BCrypt and SHA512-CRYPT hashes when valid password provided")
    void shouldUpdateBothHashesWhenValidPasswordProvided() {
        // Given
        String plainPassword = "newPassword123";
        String bcryptHash = "$2a$12$newBCryptHash";
        String sha512Hash = "$6$newSHA512Hash";

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(plainPassword)).thenReturn(bcryptHash);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(plainPassword)))
                .thenReturn(sha512Hash);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        passwordSyncService.updatePassword(1L, plainPassword);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPasswordHash()).isEqualTo(bcryptHash);
        assertThat(savedUser.getDovecotPasswordHash()).isEqualTo("{SHA512-CRYPT}" + sha512Hash);

        verify(passwordEncoder).encode(plainPassword);
        verify(jdbcTemplate).queryForObject(anyString(), eq(String.class), eq(plainPassword));
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when userId is null")
    void shouldThrowExceptionWhenUserIdIsNull() {
        // When/Then
        assertThatThrownBy(() -> passwordSyncService.updatePassword(null, "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId must not be null");

        verifyNoInteractions(userRepository, passwordEncoder, jdbcTemplate);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when plainPassword is null")
    void shouldThrowExceptionWhenPlainPasswordIsNull() {
        // When/Then
        assertThatThrownBy(() -> passwordSyncService.updatePassword(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plainPassword must not be null");

        verifyNoInteractions(userRepository, passwordEncoder, jdbcTemplate);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when plainPassword is blank")
    void shouldThrowExceptionWhenPlainPasswordIsBlank() {
        // When/Then
        assertThatThrownBy(() -> passwordSyncService.updatePassword(1L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plainPassword must not be blank");

        verifyNoInteractions(userRepository, passwordEncoder, jdbcTemplate);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> passwordSyncService.updatePassword(999L, "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found with id: 999");

        verify(userRepository).findById(999L);
        verifyNoInteractions(passwordEncoder, jdbcTemplate);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should update password by username when valid username provided")
    void shouldUpdatePasswordByUsernameWhenValidUsernameProvided() {
        // Given
        String username = "test@robin.local";
        String plainPassword = "newPassword123";
        String bcryptHash = "$2a$12$newBCryptHash";
        String sha512Hash = "$6$newSHA512Hash";

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(plainPassword)).thenReturn(bcryptHash);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(plainPassword)))
                .thenReturn(sha512Hash);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        passwordSyncService.updatePasswordByUsername(username, plainPassword);

        // Then
        verify(userRepository).findByUsername(username);
        verify(userRepository).findById(1L);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when username is null")
    void shouldThrowExceptionWhenUsernameIsNull() {
        // When/Then
        assertThatThrownBy(() -> passwordSyncService.updatePasswordByUsername(null, "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username must not be null");

        verifyNoInteractions(userRepository, passwordEncoder, jdbcTemplate);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when username is blank")
    void shouldThrowExceptionWhenUsernameIsBlank() {
        // When/Then
        assertThatThrownBy(() -> passwordSyncService.updatePasswordByUsername("   ", "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username must not be blank");

        verifyNoInteractions(userRepository, passwordEncoder, jdbcTemplate);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when user not found by username")
    void shouldThrowExceptionWhenUserNotFoundByUsername() {
        // Given
        String username = "nonexistent@robin.local";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> passwordSyncService.updatePasswordByUsername(username, "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found with username: " + username);

        verify(userRepository).findByUsername(username);
        verifyNoInteractions(passwordEncoder, jdbcTemplate);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should validate password correctly when password matches")
    void shouldValidatePasswordCorrectlyWhenPasswordMatches() {
        // Given
        String plainPassword = "correctPassword";
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(plainPassword, testUser.getPasswordHash())).thenReturn(true);

        // When
        boolean result = passwordSyncService.validatePassword(1L, plainPassword);

        // Then
        assertThat(result).isTrue();
        verify(passwordEncoder).matches(plainPassword, testUser.getPasswordHash());
    }

    @Test
    @DisplayName("should validate password correctly when password does not match")
    void shouldValidatePasswordCorrectlyWhenPasswordDoesNotMatch() {
        // Given
        String plainPassword = "wrongPassword";
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(plainPassword, testUser.getPasswordHash())).thenReturn(false);

        // When
        boolean result = passwordSyncService.validatePassword(1L, plainPassword);

        // Then
        assertThat(result).isFalse();
        verify(passwordEncoder).matches(plainPassword, testUser.getPasswordHash());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when validating with null userId")
    void shouldThrowExceptionWhenValidatingWithNullUserId() {
        // When/Then
        assertThatThrownBy(() -> passwordSyncService.validatePassword(null, "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId must not be null");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when validating with null password")
    void shouldThrowExceptionWhenValidatingWithNullPassword() {
        // When/Then
        assertThatThrownBy(() -> passwordSyncService.validatePassword(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plainPassword must not be null");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("should prepend SHA512-CRYPT prefix to Dovecot hash")
    void shouldPrependPrefixToDovecotHash() {
        // Given
        String plainPassword = "testPassword";
        String bcryptHash = "$2a$12$bcryptHash";
        String sha512Hash = "$6$rounds=5000$salt$hash";

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(plainPassword)).thenReturn(bcryptHash);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(plainPassword)))
                .thenReturn(sha512Hash);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        passwordSyncService.updatePassword(1L, plainPassword);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getDovecotPasswordHash())
                .startsWith("{SHA512-CRYPT}")
                .isEqualTo("{SHA512-CRYPT}" + sha512Hash);
    }
}
