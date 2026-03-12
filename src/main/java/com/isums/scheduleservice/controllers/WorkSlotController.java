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

    @PostMapping
    public ApiResponse<WorkSlotDto> createSlots(@RequestBody CreateWorkSlotRequest request) {
        WorkSlotDto res = workSlotService.createSlots(request);
        return ApiResponses.created(res,"Create slots successfully");
    }

}
