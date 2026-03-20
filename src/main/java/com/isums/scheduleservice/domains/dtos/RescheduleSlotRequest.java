package com.isums.scheduleservice.domains.dtos;

import com.isums.scheduleservice.domains.enums.JobType;

import java.time.LocalDateTime;
import java.util.UUID;

public record RescheduleSlotRequest(
        UUID jobId,
        JobType jobType,
        UUID newStaffId,
        LocalDateTime newStartTime,
        String reason
) {}
