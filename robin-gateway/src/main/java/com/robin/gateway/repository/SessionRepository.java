package com.robin.gateway.repository;

import com.robin.gateway.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Session entity.
 *
 * Provides CRUD operations and custom queries for session management.
 *
 * @author Robin Gateway Team
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    /**
     * Find session by refresh token.
     *
     * @param refreshToken the refresh token to search for
     * @return Optional containing the session if found
     */
    Optional<Session> findByRefreshToken(String refreshToken);

    /**
     * Find all valid sessions for a user.
     *
     * @param userId the user ID
     * @return list of valid sessions
     */
    @Query("SELECT s FROM Session s WHERE s.userId = :userId AND s.revoked = false AND s.expiresAt > :now")
    List<Session> findValidSessionsByUserId(Long userId, LocalDateTime now);

    /**
     * Find all sessions for a user.
     *
     * @param userId the user ID
     * @return list of all sessions
     */
    List<Session> findByUserId(Long userId);

    /**
     * Revoke all sessions for a user.
     *
     * @param userId the user ID
     * @return number of sessions revoked
     */
    @Modifying
    @Transactional
    @Query("UPDATE Session s SET s.revoked = true, s.revokedAt = :revokedAt WHERE s.userId = :userId AND s.revoked = false")
    int revokeAllUserSessions(Long userId, LocalDateTime revokedAt);

    /**
     * Delete expired sessions.
     *
     * @param expirationTime the cutoff time for deletion
     * @return number of sessions deleted
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Session s WHERE s.expiresAt < :expirationTime OR (s.revoked = true AND s.revokedAt < :expirationTime)")
    int deleteExpiredSessions(LocalDateTime expirationTime);

    /**
     * Count active sessions for a user.
     *
     * @param userId the user ID
     * @param now current time
     * @return count of active sessions
     */
    @Query("SELECT COUNT(s) FROM Session s WHERE s.userId = :userId AND s.revoked = false AND s.expiresAt > :now")
    long countActiveSessionsByUserId(Long userId, LocalDateTime now);
}
