package com.isums.scheduleservice.domains.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record SlotDto(

        UUID id,

        UUID staffId,

        LocalDateTime startTime,

        LocalDateTime endTime,

        String status

) {}
