package org.example.matcheat.domain.account.entity;

import org.example.matcheat.domain.account.enums.UserStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountEntitySuspensionTest {
    @Test
    void timedPenaltyExpiryDoesNotReleaseManualBlacklist() {
        var user = UserAccountEntity.fromDomain(UserAccount.registerUser(
                "user@example.com", "{bcrypt}hash", "User"));
        user.changeStatus(UserStatus.SUSPENDED);
        user.suspendForPenalty();

        user.activateAfterPenalty();

        assertThat(user.toDomain().status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.manualSuspension()).isTrue();
    }

    @Test
    void timedPenaltyExpiryReleasesPenaltyOnlySuspension() {
        var user = UserAccountEntity.fromDomain(UserAccount.registerUser(
                "user@example.com", "{bcrypt}hash", "User"));
        user.suspendForPenalty();

        user.activateAfterPenalty();

        assertThat(user.toDomain().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.manualSuspension()).isFalse();
    }
}
