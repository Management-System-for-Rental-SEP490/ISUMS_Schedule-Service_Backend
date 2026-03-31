package com.isums.scheduleservice.controllers;

import com.isums.scheduleservice.domains.dtos.*;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedules/work_slots")
@RequiredArgsConstructor
public class WorkSlotController {
    private final WorkSlotService workSlotService;

    @PostMapping("/manual")
    public ApiResponse<WorkSlotDto> manualAssign(@RequestBody ManualAssignRequest request) {
        WorkSlotDto res = workSlotService.manualAssign(request);
        return ApiResponses.created(res,"Create slots successfully");
    }

//    @PostMapping("/reschedule")
//    public ApiResponse<WorkSlotDto> rescheduleSlot(@RequestBody RescheduleSlotRequest request){
//        WorkSlotDto res = workSlotService.rescheduleSlot(request);
//        return ApiResponses.ok(res,"Reschedule job successfully");
//    }

    @PostMapping("/confirm")
    public ApiResponse<WorkSlotDto> confirmSlot(@RequestBody ConfirmSlotRequest request){
        WorkSlotDto res = workSlotService.confirmSlot(request);
        return ApiResponses.ok(res,"Confirm successfully");
    }

    @PostMapping("/staff/confirm")
    public ApiResponse<WorkSlotDto> confirmTime(@RequestBody ConfirmSlotRequest request){
        WorkSlotDto res = workSlotService.staffConfirmTime(request);
        return ApiResponses.ok(res,"Confirm time for schedule successfully");
    }

    @PostMapping("/manager/confirm/{jobId}")
    public ApiResponse<WorkSlotDto> confirmSchedule(@PathVariable UUID jobId){
        WorkSlotDto res = workSlotService.confirmSlotForStaff(jobId);
        return ApiResponses.ok(res,"Confirm time for schedule successfully");
    }

    @GetMapping("/staff")
    public ApiResponse<List<WorkSlotDto>> getSlotsByStaff(@AuthenticationPrincipal Jwt jwt){
        List<WorkSlotDto> res = workSlotService.getSlotsByStaffId(jwt.getSubject());
        return ApiResponses.ok(res,"Get staff slots successfully");
    }

    @GetMapping("/{slotId}")
    public ApiResponse<WorkSlotDto> getSlotById(@PathVariable UUID slotId){
        WorkSlotDto res = workSlotService.getSlotById(slotId);
        return ApiResponses.ok(res,"Get slots by id successfully");
    }

    @PutMapping("/{slotId}/cancel")
    public ApiResponse<Boolean> cancelSlot(@PathVariable UUID slotId){
        Boolean res = workSlotService.cancelSlot(slotId);
        return ApiResponses.ok(true,"Slot cancelled successfully");
    }
    @GetMapping("/current")
    public ApiResponse<List<WorkSlotDto>> getSlotsByRange(@RequestParam LocalDate start, @RequestParam LocalDate end) {
        List<WorkSlotDto> res = workSlotService.getSlotsByRange(start, end);
        return ApiResponses.ok(res, "Get slots successfully");
    }

    @GetMapping("/generate")
    public ApiResponse<List<DaySlotDto>> generateSlots(@RequestParam LocalDate start, @RequestParam LocalDate end) {
        List<DaySlotDto> res = workSlotService.generateSlots(start,end);
        return ApiResponses.ok(res,"Generate successfully");
    }


}
