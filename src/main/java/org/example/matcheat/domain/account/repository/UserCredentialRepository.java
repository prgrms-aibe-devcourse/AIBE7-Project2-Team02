package org.example.matcheat.domain.account.repository;

import org.example.matcheat.domain.account.entity.UserAccount;
import org.example.matcheat.domain.account.enums.UserRole;

import java.util.Optional;
import java.time.Instant;

public interface UserCredentialRepository {
    Optional<UserAccount> findById(long userId);

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(UserRole role);

    default Optional<String> findManualSuspensionReason(long userId) { return Optional.empty(); }

    UserAccount save(UserAccount account);

    Optional<UserAccount> updateName(long userId, String name);

    default Optional<UserAccount> updatePassword(long userId, String passwordHash) {
        return Optional.empty();
    }

    Optional<UserAccount> withdraw(long userId, Instant withdrawnAt);
}
