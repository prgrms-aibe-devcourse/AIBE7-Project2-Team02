package org.example.matcheat.domain.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountPenaltyRequest(
        int days,
        @NotBlank @Size(max = 500) String reason) {}
