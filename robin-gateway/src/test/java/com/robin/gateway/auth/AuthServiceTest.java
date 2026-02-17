package com.robin.gateway.auth;

import com.robin.gateway.model.Session;
import com.robin.gateway.model.User;
import com.robin.gateway.model.dto.AuthResponse;
import com.robin.gateway.model.dto.LoginRequest;
import com.robin.gateway.model.dto.TokenResponse;
import com.robin.gateway.repository.SessionRepository;
import com.robin.gateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("test@example.com")
                .passwordHash("hashedPassword")
                .enabled(true)
                .roles(Set.of("USER"))
                .build();

        loginRequest = LoginRequest.builder()
                .username("test@example.com")
                .password("password123")
                .build();
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void testLoginSuccess() {
        // Given
        when(userRepository.findByUsername(loginRequest.username())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.password(), testUser.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(testUser)).thenReturn("refresh-token");

        // When
        AuthResponse response = authService.login(loginRequest, "127.0.0.1", "UserAgent");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.tokens().accessToken()).isEqualTo("access-token");
        assertThat(response.user().username()).isEqualTo(testUser.getUsername());
        
        verify(sessionRepository).save(any(Session.class));
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should throw BadCredentialsException for invalid username")
    void testLoginInvalidUsername() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest, "127.0.0.1", "UserAgent"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Should throw DisabledException for disabled user")
    void testLoginDisabledUser() {
        testUser.setEnabled(false);
        when(userRepository.findByUsername(loginRequest.username())).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.login(loginRequest, "127.0.0.1", "UserAgent"))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    @DisplayName("Should refresh token successfully")
    void testRefreshTokenSuccess() {
        // Given
        String refreshToken = "valid-refresh-token";
        Session session = Session.builder()
                .userId(1L)
                .refreshToken(refreshToken)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build();

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getTokenType(refreshToken)).thenReturn("refresh");
        when(sessionRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.of(session));
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("new-access-token");

        // When
        TokenResponse response = authService.refreshToken(refreshToken);

        // Then
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        verify(jwtTokenProvider).generateAccessToken(testUser);
    }

    @Test
    @DisplayName("Should logout and revoke session")
    void testLogout() {
        String refreshToken = "valid-refresh-token";
        Session session = Session.builder().userId(1L).build();
        
        when(sessionRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.of(session));

        authService.logout(refreshToken);

        verify(sessionRepository).save(argThat(Session::getRevoked));
    }
}
