package com.isums.scheduleservice.domains.dtos;

import java.time.LocalDate;
import java.util.UUID;

public record CreateLeaveRequest(
        UUID staffId,
        LocalDate leaveDate,
        String note
) {
}
