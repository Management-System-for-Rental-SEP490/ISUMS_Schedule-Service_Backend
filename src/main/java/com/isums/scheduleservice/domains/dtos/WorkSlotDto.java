package com.isums.scheduleservice.domains.dtos;

import java.util.List;

public record WorkSlotDto(
        int totalSlotsCreated,
        List<SlotTimeDto> slots

) {
}
