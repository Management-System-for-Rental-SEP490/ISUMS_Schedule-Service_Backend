package com.isums.scheduleservice.controllers;

import com.isums.scheduleservice.domains.dtos.*;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/schedules/work_slots")
@RequiredArgsConstructor
public class WorkSlotController {
    private final WorkSlotService workSlotService;

    @PostMapping
    public ApiResponse<WorkSlotDto> createSlots(@RequestBody CreateWorkSlotRequest request) {
        WorkSlotDto res = workSlotService.createSlots(request);
        return ApiResponses.created(res,"Create slots successfully");
    }

    @GetMapping("/staff/{staffId}")
    public ApiResponse<List<WorkSlotDto>> getSlotsByStaff(@PathVariable UUID staffId){
        List<WorkSlotDto> res = workSlotService.getSlotsByStaffId(staffId);
        return ApiResponses.ok(res,"Get staff slots successfully");
    }

    @GetMapping
    public ApiResponse<List<WorkSlotDto>> getSlotsByDate(@RequestParam LocalDate date){
        List<WorkSlotDto> res = workSlotService.getSlotsByDate(date);
        return ApiResponses.ok(res,"Get slots by date");
    }

    @PutMapping("/{slotId}/cancel")
    public ApiResponse<Boolean> cancelSlot(@PathVariable UUID slotId){
        Boolean res = workSlotService.cancelSlot(slotId);
        return ApiResponses.ok(true,"Slot cancelled successfully");
    }

}
