package com.isums.scheduleservice.domains.dtos;

import com.isums.scheduleservice.domains.enums.JobType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ManualAssignRequest(
        UUID jobId,
        UUID staffId,
        LocalDateTime startTime,
        JobType jobType
) {
}
