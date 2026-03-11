package com.isums.scheduleservice.domains.dtos;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record ScheduleTemplateDto(
        UUID id,
        String workingDays,
        LocalTime openTime,
        LocalTime breakStart,
        LocalTime breakEnd,
        LocalTime closeTime,
        Integer slotMinutes,
        Integer bufferMinutes,
        LocalDate effectiveFrom,
        Instant updatedAt

) {}
