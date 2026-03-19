package com.isums.scheduleservice.infrastructures.abstracts;

import com.isums.scheduleservice.domains.dtos.CreateWorkSlotRequest;
import com.isums.scheduleservice.domains.dtos.DaySlotDto;
import com.isums.scheduleservice.domains.dtos.RescheduleSlotRequest;
import com.isums.scheduleservice.domains.dtos.WorkSlotDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WorkSlotService {
    WorkSlotDto createSlots(CreateWorkSlotRequest req);
    List<WorkSlotDto> getSlotsByStaffId(UUID staffId);
    //List<WorkSlotDto> getSlotsByDate(LocalDate date);
    Boolean cancelSlot(UUID slotId);
    WorkSlotDto getSlotById(UUID workSlotId);
    WorkSlotDto rescheduleSlot(RescheduleSlotRequest request);
    List<WorkSlotDto> getSlotsByRange(LocalDate start,LocalDate end);
    List<DaySlotDto> generateSlots(LocalDate start, LocalDate end);
}
