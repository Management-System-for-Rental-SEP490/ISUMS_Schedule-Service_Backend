package com.isums.scheduleservice.infrastructures.abstracts;

import com.isums.scheduleservice.domains.dtos.CreateWorkSlotRequest;
import com.isums.scheduleservice.domains.dtos.WorkSlotDto;

import java.util.List;
import java.util.UUID;

public interface WorkSlotService {
    WorkSlotDto createSlots(CreateWorkSlotRequest req);
}
