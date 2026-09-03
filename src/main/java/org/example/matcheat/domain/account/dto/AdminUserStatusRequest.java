package org.example.matcheat.domain.account.dto;

import jakarta.validation.constraints.NotNull;
import org.example.matcheat.domain.account.enums.UserStatus;

public record AdminUserStatusRequest(@NotNull UserStatus status, String reason) {
}
