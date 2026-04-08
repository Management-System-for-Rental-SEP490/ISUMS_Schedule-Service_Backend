package com.isums.scheduleservice.domains.dtos;

import java.time.LocalTime;

public record SlotsAvailableDto(
        LocalTime startTime,
        LocalTime endTime,
        String status,
        int availableStaffCount
) {}
