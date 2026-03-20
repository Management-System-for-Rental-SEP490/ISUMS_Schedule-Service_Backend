package com.isums.scheduleservice.domains.dtos;

import java.time.LocalDate;
import java.util.List;

public record DaySlotDto(
        LocalDate date,
        List<SlotsAvailableDto> slots
) {
}
