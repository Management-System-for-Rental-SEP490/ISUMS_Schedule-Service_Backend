package com.isums.scheduleservice.domains.dtos;

import java.util.UUID;

public record StaffDto(
        UUID staffId,
        UserDto info
) {
}
