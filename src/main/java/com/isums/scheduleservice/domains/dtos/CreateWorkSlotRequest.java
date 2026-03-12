package com.isums.scheduleservice.domains.dtos;

import com.isums.scheduleservice.domains.enums.JobType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CreateWorkSlotRequest(
        UUID staffId,
        UUID jobId,
        JobType jobType,
        LocalDateTime startTime

) {
}
