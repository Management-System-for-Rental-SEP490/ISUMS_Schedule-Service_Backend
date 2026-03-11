package com.isums.scheduleservice.domains.dtos;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateWorkSlotRequest(
        List<UUID> staffIds,
        LocalDate date,
        Integer days
) {
}
