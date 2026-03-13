package com.isums.scheduleservice.domains.dtos;

import com.isums.scheduleservice.domains.enums.LeaveRequestStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestDto(
        UUID id,
        UUID staffId,
        LocalDate leaveDate,
        String note,
        LeaveRequestStatus status,
        UUID managerId,
        String decisionNote,
        Instant createdAt
) {
}
