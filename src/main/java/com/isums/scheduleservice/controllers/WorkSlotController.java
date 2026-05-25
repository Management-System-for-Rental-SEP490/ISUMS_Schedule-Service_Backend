package com.isums.scheduleservice.controllers;

import com.isums.scheduleservice.domains.dtos.*;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import com.isums.scheduleservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.userservice.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedules/work_slots")
@RequiredArgsConstructor
public class WorkSlotController {
    private final WorkSlotService workSlotService;
    private final UserClientsGrpc userClientsGrpc;

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

    @PostMapping("/confirm-maintenance")
    public ApiResponse<WorkSlotDto> confirmSlot(@RequestBody ConfirmSlotRequest request){
        WorkSlotDto res = workSlotService.confirmSlot(request);
        return ApiResponses.ok(res,"Confirm successfully");
    }

    @PostMapping("/staff/confirm")
    public ApiResponse<WorkSlotDto> confirmTime(@RequestBody ConfirmSlotRequest request){
        WorkSlotDto res = workSlotService.staffConfirmTime(request);
        return ApiResponses.ok(res,"Confirm time for schedule successfully");
    }

    @PostMapping("/manager/confirm-issue/{jobId}")
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

    @GetMapping("/slots")
    public ApiResponse<List<DaySlotDto>> getSlots(@RequestParam UUID jobId, @RequestParam LocalDate date) {
        List<DaySlotDto> res = workSlotService.getSlotsByDate(jobId, date);
        return ApiResponses.ok(res, "Get slots successfully");
    }

    @GetMapping("/slots/staff")
    public ApiResponse<List<StaffDto>> getAvailableStaff(@RequestParam UUID jobId, @RequestParam LocalDate date, @RequestParam LocalTime startTime) {
        List<StaffDto> res = workSlotService.getAvailableStaff(jobId, date, startTime);
        return ApiResponses.ok(res, "Get available staff");
    }

    @GetMapping("/slots/me")
    public ApiResponse<List<DaySlotDto>> getMySlots(@AuthenticationPrincipal Jwt jwt, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        UserResponse user  = userClientsGrpc.getUserIdAndRoleByKeyCloakId(jwt.getSubject());
        List<DaySlotDto> res = workSlotService.getMyAvailableSlotsRange(user.getId(),startDate,endDate);
        return ApiResponses.ok(res, "Get my slots");
    }

    @PostMapping("/admin/republish-inspection-scheduled")
    public ApiResponse<Integer> republishInspectionScheduled() {
        int published = workSlotService.republishInspectionScheduled();
        return ApiResponses.ok(published, "Republished " + published + " job.scheduled events for BOOKED inspection slots");
    }
}
