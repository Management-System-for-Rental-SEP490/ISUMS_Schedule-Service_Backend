package com.isums.scheduleservice.infrastructures.abstracts;

import com.isums.scheduleservice.domains.dtos.*;
import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.domains.events.SlotEvent;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WorkSlotService {
    //WorkSlotDto createSlots(CreateWorkSlotRequest req);
    WorkSlotDto manualAssign(ManualAssignRequest req);
    WorkSlotDto confirmSlot(ConfirmSlotRequest req);
    WorkSlotDto staffConfirmTime(ConfirmSlotRequest req);
    WorkSlotDto confirmSlotForStaff(UUID jobId);
    List<WorkSlotDto> getSlotsByStaffId(String staffId);
    Boolean cancelSlot(UUID slotId);
    WorkSlotDto getSlotById(UUID workSlotId);
    WorkSlotDto rescheduleSlot(RescheduleSlotRequest request);
    List<WorkSlotDto> getSlotsByRange(LocalDate start,LocalDate end);
    List<DaySlotDto> generateSlots(LocalDate start, LocalDate end);
    List<DaySlotDto> generateSlotsForStaff(String staffId, LocalDate start, LocalDate end);
    void markSlotDone(JobEvent event);
    void handleAutoAssign(JobEvent event);
}
