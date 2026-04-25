package com.isums.scheduleservice.controllers;

import com.isums.scheduleservice.domains.dtos.ApiResponse;
import com.isums.scheduleservice.domains.dtos.ApiResponses;
import com.isums.scheduleservice.domains.dtos.MonthlyJobsRequest;
import com.isums.scheduleservice.services.StaffAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedules/staff-assignments")
public class StaffAssignmentController {
    private final StaffAssignmentService staffAssignmentService;

    @PostMapping("/monthly-jobs")
    public ApiResponse<Map<UUID, Long>> calculateMonthlyJobs(@RequestBody MonthlyJobsRequest request) {
        Map<UUID, Long> res = staffAssignmentService.calculateMonthlyJobs(request.staffIds());
        return ApiResponses.ok(res, "Calculate monthly jobs successfully");
    }
}
