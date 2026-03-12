package com.isums.scheduleservice.domains.dtos;

import java.time.LocalTime;

public record SlotPattern(
        LocalTime start,
        LocalTime end

) {
}
