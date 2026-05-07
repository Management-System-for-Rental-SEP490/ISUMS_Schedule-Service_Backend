package com.isums.scheduleservice.domains.dtos;

import java.util.UUID;

public record UserDto(
        UUID id,
        String name,
        String email,
        String phone
) {
}
