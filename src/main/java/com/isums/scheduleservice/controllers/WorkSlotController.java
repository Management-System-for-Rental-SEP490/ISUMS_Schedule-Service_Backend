package com.isums.scheduleservice.controllers;

import com.isums.scheduleservice.domains.dtos.*;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/schedules/work_slots")
@RequiredArgsConstructor
public class WorkSlotController {
    private final WorkSlotService workSlotService;

    @PostMapping("/create")
    public ApiResponse<WorkSlotDto> createSlots(@RequestBody CreateWorkSlotRequest request
    ) {
        WorkSlotDto res = workSlotService.createSlots(request);
        return ApiResponses.created(res,"Create slots successfully");
    }

    @GetMapping
    public ApiResponse<List<SlotDto>> getAllSlots(){
        List<SlotDto> res = workSlotService.getAllWorkSlots();
        return ApiResponses.ok(res,"get all slots successfully");
    }

    @GetMapping("/staff/{staffId}")
    public ApiResponse<List<SlotDto>> getSlotsByStaff(@PathVariable UUID staffId){
        List<SlotDto> res = workSlotService.getWorkSlotsByStaff(staffId);
        return ApiResponses.ok(res,"get all slots by staff successfully");
    }
}
