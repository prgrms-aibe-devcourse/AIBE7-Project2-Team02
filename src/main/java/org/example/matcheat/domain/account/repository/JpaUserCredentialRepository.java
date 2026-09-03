package org.example.matcheat.domain.account.repository;

import org.example.matcheat.domain.account.entity.UserAccount;
import org.example.matcheat.domain.account.entity.UserAccountEntity;
import org.example.matcheat.domain.account.enums.UserRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.Instant;

@Repository
public class JpaUserCredentialRepository implements UserCredentialRepository {
    private final UserAccountJpaRepository repository;

    public JpaUserCredentialRepository(UserAccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UserAccount> findById(long userId) {
        return repository.findById(userId).map(UserAccountEntity::toDomain);
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return repository.findByEmail(email).map(UserAccountEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public boolean existsByRole(UserRole role) {
        return repository.existsByRole(role);
    }

    @Override
    public Optional<String> findManualSuspensionReason(long userId) {
        return repository.findById(userId).map(UserAccountEntity::manualSuspensionReason);
    }

    @Override
    public UserAccount save(UserAccount account) {
        try {
            return repository.saveAndFlush(UserAccountEntity.fromDomain(account)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateUserEmailException(exception);
        }
    }

    @Override
    public Optional<UserAccount> updateName(long userId, String name) {
        return repository.findById(userId).map(entity -> {
            entity.changeName(name);
            return entity.toDomain();
        });
    }

    @Override
    public Optional<UserAccount> updatePassword(long userId, String passwordHash) {
        return repository.findById(userId).map(entity -> {
            entity.changePassword(passwordHash);
            return entity.toDomain();
        });
    }

    @Override
    public Optional<UserAccount> withdraw(long userId, Instant withdrawnAt) {
        return repository.findById(userId).map(entity -> {
            entity.withdraw(withdrawnAt);
            return entity.toDomain();
        });
    }
}
