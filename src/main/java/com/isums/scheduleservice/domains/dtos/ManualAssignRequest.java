package com.isums.scheduleservice.domains.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record ManualAssignRequest(
        UUID jobId,
        UUID staffId,
        LocalDateTime startTime
) {
}
