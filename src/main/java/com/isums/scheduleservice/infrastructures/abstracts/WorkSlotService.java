package com.isums.scheduleservice.infrastructures.abstracts;

import com.isums.scheduleservice.domains.dtos.*;
import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.domains.events.SlotEvent;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WorkSlotService {
    WorkSlotDto createSlots(CreateWorkSlotRequest req);
    WorkSlotDto confirmSlot(ConfirmSlotRequest req);
    List<WorkSlotDto> getSlotsByStaffId(String staffId);
    Boolean cancelSlot(UUID slotId);
    WorkSlotDto getSlotById(UUID workSlotId);
    WorkSlotDto rescheduleSlot(RescheduleSlotRequest request);
    List<WorkSlotDto> getSlotsByRange(LocalDate start,LocalDate end);
    List<DaySlotDto> generateSlots(LocalDate start, LocalDate end);
    void markSlotDone(SlotEvent event);
    void handleAutoAssign(JobEvent event);
}
