package com.isums.scheduleservice.domains.dtos;

import com.isums.scheduleservice.domains.enums.JobType;
import com.isums.scheduleservice.domains.enums.SlotStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record WorkSlotDto(
        UUID id,
        UUID staffId,
        UUID jobId,
        JobType jobType,
        LocalDateTime startTime,
        LocalDateTime endTime,
        SlotStatus status
) {
}
