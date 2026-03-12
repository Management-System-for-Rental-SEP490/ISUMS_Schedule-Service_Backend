package com.isums.scheduleservice.domains.dtos;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateScheduleTemplateRequest(
        String workingDays,
        LocalTime openTime,
        LocalTime breakStart,
        LocalTime breakEnd,
        LocalTime closeTime,
        Integer slotMinutes,
        Integer bufferMinutes,
        LocalDate effectiveFrom
) {
}
