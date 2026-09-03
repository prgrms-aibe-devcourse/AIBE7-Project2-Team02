package org.example.matcheat.domain.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuspensionAppealRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank @Size(max = 2000) String message) {
}
