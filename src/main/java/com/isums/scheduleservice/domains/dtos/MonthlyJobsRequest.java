package com.isums.scheduleservice.domains.dtos;

import java.util.List;
import java.util.UUID;

public record MonthlyJobsRequest(List<UUID> staffIds) {
}
