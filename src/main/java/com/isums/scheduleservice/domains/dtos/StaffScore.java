package com.isums.scheduleservice.domains.dtos;

import java.util.UUID;

public record StaffScore(
        UUID staffId,
        long monthlyJobs,
        long activeJobs
) {
}
