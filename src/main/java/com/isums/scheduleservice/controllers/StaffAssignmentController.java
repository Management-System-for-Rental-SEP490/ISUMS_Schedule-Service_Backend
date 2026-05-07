package com.isums.scheduleservice.controllers;

import com.isums.scheduleservice.domains.dtos.ApiResponse;
import com.isums.scheduleservice.domains.dtos.ApiResponses;
import com.isums.scheduleservice.services.StaffAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedules/staff-assignments")
public class StaffAssignmentController {
    private final StaffAssignmentService staffAssignmentService;

    @GetMapping("/monthly-jobs")
    public ApiResponse<Map<UUID, Long>> calculateMonthlyJobs(@RequestParam List<UUID> staffIds) {
        Map<UUID, Long> res = staffAssignmentService.calculateMonthlyJobs(staffIds);
        return ApiResponses.ok(res, "Calculate monthly jobs successfully");
    }
}
