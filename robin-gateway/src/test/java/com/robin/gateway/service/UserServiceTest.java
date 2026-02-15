package com.robin.gateway.service;

import com.robin.gateway.model.User;
import com.robin.gateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for UserService.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>User CRUD operations</li>
 *   <li>Password management integration</li>
 *   <li>Error handling</li>
 *   <li>Reactive operations</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordSyncService passwordSyncService;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .passwordHash("hashedpassword")
                .dovecotPasswordHash("{SHA512-CRYPT}...")
                .quotaBytes(1073741824L) // 1GB
                .enabled(true)
                .roles(Set.of("USER"))
                .permissions(Set.of("READ"))
                .build();
    }

    // ==================== Get All Users Tests ====================

    @Test
    @DisplayName("Should retrieve all users successfully")
    void testGetAllUsersSuccess() {
        // Arrange
        User user2 = User.builder()
                .id(2L)
                .username("user2")
                .enabled(true)
                .build();
        List<User> users = Arrays.asList(testUser, user2);
        when(userRepository.findAll()).thenReturn(users);

        // Act & Assert
        StepVerifier.create(userService.getAllUsers())
                .expectNext(testUser)
                .expectNext(user2)
                .verifyComplete();

        verify(userRepository).findAll();
    }

    @Test
    @DisplayName("Should return empty flux when no users exist")
    void testGetAllUsersEmpty() {
        // Arrange
        when(userRepository.findAll()).thenReturn(Arrays.asList());

        // Act & Assert
        StepVerifier.create(userService.getAllUsers())
                .verifyComplete();

        verify(userRepository).findAll();
    }

    @Test
    @DisplayName("Should handle repository error when getting all users")
    void testGetAllUsersError() {
        // Arrange
        when(userRepository.findAll()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        StepVerifier.create(userService.getAllUsers())
                .expectError(RuntimeException.class)
                .verify();
    }

    // ==================== Get User By Username Tests ====================

    @Test
    @DisplayName("Should retrieve user by username successfully")
    void testGetUserSuccess() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act & Assert
        StepVerifier.create(userService.getUser("testuser"))
                .expectNext(testUser)
                .verifyComplete();

        verify(userRepository).findByUsername("testuser");
    }

    @Test
    @DisplayName("Should return empty mono when user not found")
    void testGetUserNotFound() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        StepVerifier.create(userService.getUser("nonexistent"))
                .verifyComplete();

        verify(userRepository).findByUsername("nonexistent");
    }

    @Test
    @DisplayName("Should handle repository error when getting user")
    void testGetUserError() {
        // Arrange
        when(userRepository.findByUsername(anyString()))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        StepVerifier.create(userService.getUser("testuser"))
                .expectError(RuntimeException.class)
                .verify();
    }

    // ==================== Create User Tests ====================

    @Test
    @DisplayName("Should create user successfully with password sync")
    void testCreateUserSuccess() {
        // Arrange
        User newUser = User.builder()
                .username("newuser")
                .passwordHash("plainpassword")
                .quotaBytes(2147483648L) // 2GB
                .enabled(true)
                .build();

        User savedUser = User.builder()
                .id(3L)
                .username("newuser")
                .quotaBytes(2147483648L)
                .enabled(true)
                .build();

        User userWithPassword = User.builder()
                .id(3L)
                .username("newuser")
                .passwordHash("bcrypt_hash")
                .dovecotPasswordHash("{SHA512-CRYPT}hash")
                .quotaBytes(2147483648L)
                .enabled(true)
                .build();

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        doNothing().when(passwordSyncService).updatePassword(eq(3L), eq("plainpassword"));
        when(userRepository.findById(3L)).thenReturn(Optional.of(userWithPassword));

        // Act & Assert
        StepVerifier.create(userService.createUser(newUser))
                .expectNext(userWithPassword)
                .verifyComplete();

        verify(userRepository).existsByUsername("newuser");
        verify(userRepository).save(any(User.class));
        verify(passwordSyncService).updatePassword(3L, "plainpassword");
        verify(userRepository).findById(3L);
    }

    @Test
    @DisplayName("Should reject creating user with existing username")
    void testCreateUserDuplicateUsername() {
        // Arrange
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        User duplicateUser = User.builder()
                .username("testuser")
                .passwordHash("password")
                .build();

        // Act & Assert
        StepVerifier.create(userService.createUser(duplicateUser))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().contains("Username already exists"))
                .verify();

        verify(userRepository).existsByUsername("testuser");
        verify(userRepository, never()).save(any());
        verify(passwordSyncService, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    @DisplayName("Should handle password sync failure during user creation")
    void testCreateUserPasswordSyncFailure() {
        // Arrange
        User newUser = User.builder()
                .username("newuser")
                .passwordHash("plainpassword")
                .build();

        User savedUser = User.builder()
                .id(3L)
                .username("newuser")
                .build();

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        doThrow(new RuntimeException("Password sync failed"))
                .when(passwordSyncService).updatePassword(anyLong(), anyString());

        // Act & Assert
        StepVerifier.create(userService.createUser(newUser))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Password sync failed"))
                .verify();
    }

    // ==================== Update User Tests ====================

    @Test
    @DisplayName("Should update user successfully without password change")
    void testUpdateUserWithoutPassword() {
        // Arrange
        User updates = User.builder()
                .quotaBytes(5368709120L) // 5GB
                .enabled(false)
                .roles(Set.of("ADMIN"))
                .permissions(Set.of("READ", "WRITE"))
                .build();

        User updatedUser = User.builder()
                .id(1L)
                .username("testuser")
                .passwordHash("hashedpassword")
                .dovecotPasswordHash("{SHA512-CRYPT}...")
                .quotaBytes(5368709120L)
                .enabled(false)
                .roles(Set.of("ADMIN"))
                .permissions(Set.of("READ", "WRITE"))
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        // Act & Assert
        StepVerifier.create(userService.updateUser("testuser", updates))
                .expectNext(updatedUser)
                .verifyComplete();

        verify(userRepository).findByUsername("testuser");
        verify(userRepository).save(any(User.class));
        verify(passwordSyncService, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    @DisplayName("Should update user with password change")
    void testUpdateUserWithPassword() {
        // Arrange
        User updates = User.builder()
                .passwordHash("newplainpassword")
                .quotaBytes(3221225472L) // 3GB
                .build();

        User userAfterPasswordUpdate = User.builder()
                .id(1L)
                .username("testuser")
                .passwordHash("new_bcrypt_hash")
                .dovecotPasswordHash("{SHA512-CRYPT}newhash")
                .quotaBytes(1073741824L)
                .enabled(true)
                .build();

        User finalUser = User.builder()
                .id(1L)
                .username("testuser")
                .passwordHash("new_bcrypt_hash")
                .dovecotPasswordHash("{SHA512-CRYPT}newhash")
                .quotaBytes(3221225472L)
                .enabled(true)
                .build();

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(testUser))
                .thenReturn(Optional.of(userAfterPasswordUpdate));
        when(userRepository.findById(1L)).thenReturn(Optional.of(userAfterPasswordUpdate));
        doNothing().when(passwordSyncService).updatePassword(1L, "newplainpassword");
        when(userRepository.save(any(User.class))).thenReturn(finalUser);

        // Act & Assert
        StepVerifier.create(userService.updateUser("testuser", updates))
                .expectNext(finalUser)
                .verifyComplete();

        verify(passwordSyncService).updatePassword(1L, "newplainpassword");
    }

    @Test
    @DisplayName("Should reject updating non-existent user")
    void testUpdateUserNotFound() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        User updates = User.builder()
                .quotaBytes(1234567890L)
                .build();

        // Act & Assert
        StepVerifier.create(userService.updateUser("nonexistent", updates))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().contains("User not found"))
                .verify();

        verify(userRepository).findByUsername("nonexistent");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle empty password during update")
    void testUpdateUserWithEmptyPassword() {
        // Arrange
        User updates = User.builder()
                .passwordHash("") // Empty password should be ignored
                .quotaBytes(2147483648L)
                .build();

        User updatedUser = User.builder()
                .id(1L)
                .username("testuser")
                .passwordHash("hashedpassword") // Original password unchanged
                .quotaBytes(2147483648L)
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        // Act & Assert
        StepVerifier.create(userService.updateUser("testuser", updates))
                .expectNext(updatedUser)
                .verifyComplete();

        verify(passwordSyncService, never()).updatePassword(anyLong(), anyString());
    }

    // ==================== Delete User Tests ====================

    @Test
    @DisplayName("Should delete user successfully")
    void testDeleteUserSuccess() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        doNothing().when(userRepository).delete(testUser);

        // Act & Assert
        StepVerifier.create(userService.deleteUser("testuser"))
                .verifyComplete();

        verify(userRepository).findByUsername("testuser");
        verify(userRepository).delete(testUser);
    }

    @Test
    @DisplayName("Should reject deleting non-existent user")
    void testDeleteUserNotFound() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        StepVerifier.create(userService.deleteUser("nonexistent"))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().contains("User not found"))
                .verify();

        verify(userRepository).findByUsername("nonexistent");
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should handle repository error during deletion")
    void testDeleteUserRepositoryError() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        doThrow(new RuntimeException("Database error")).when(userRepository).delete(any());

        // Act & Assert
        StepVerifier.create(userService.deleteUser("testuser"))
                .expectError(RuntimeException.class)
                .verify();
    }

    // ==================== Edge Cases and Integration Tests ====================

    @Test
    @DisplayName("Should handle null updates gracefully")
    void testUpdateUserWithNullFields() {
        // Arrange
        User updates = User.builder()
                .passwordHash(null)
                .quotaBytes(null)
                .enabled(null)
                .roles(null)
                .permissions(null)
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act & Assert
        StepVerifier.create(userService.updateUser("testuser", updates))
                .expectNext(testUser)
                .verifyComplete();

        verify(passwordSyncService, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    @DisplayName("Should handle empty roles and permissions during update")
    void testUpdateUserWithEmptyCollections() {
        // Arrange
        User updates = User.builder()
                .roles(Set.of())
                .permissions(Set.of())
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act & Assert
        StepVerifier.create(userService.updateUser("testuser", updates))
                .expectNext(testUser)
                .verifyComplete();

        // Empty collections should not update roles/permissions
        verify(userRepository).save(argThat(user ->
                user.getRoles().equals(testUser.getRoles()) &&
                user.getPermissions().equals(testUser.getPermissions())));
    }

    @Test
    @DisplayName("Should create user with minimal fields")
    void testCreateUserMinimalFields() {
        // Arrange
        User minimalUser = User.builder()
                .username("minimal")
                .passwordHash("password")
                .build();

        User savedMinimal = User.builder()
                .id(5L)
                .username("minimal")
                .build();

        User withPassword = User.builder()
                .id(5L)
                .username("minimal")
                .passwordHash("bcrypt")
                .dovecotPasswordHash("sha512crypt")
                .build();

        when(userRepository.existsByUsername("minimal")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedMinimal);
        doNothing().when(passwordSyncService).updatePassword(anyLong(), anyString());
        when(userRepository.findById(5L)).thenReturn(Optional.of(withPassword));

        // Act & Assert
        StepVerifier.create(userService.createUser(minimalUser))
                .expectNext(withPassword)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle concurrent user operations")
    void testConcurrentOperations() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act - Simulate concurrent reads
        Mono<User> read1 = userService.getUser("testuser");
        Mono<User> read2 = userService.getUser("testuser");
        Mono<User> read3 = userService.getUser("testuser");

        // Assert - All should complete successfully
        StepVerifier.create(Flux.merge(read1, read2, read3))
                .expectNextCount(3)
                .verifyComplete();

        verify(userRepository, times(3)).findByUsername("testuser");
    }
}
