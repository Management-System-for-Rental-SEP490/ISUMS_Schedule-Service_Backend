package com.isums.scheduleservice.controllers;


import com.isums.scheduleservice.domains.dtos.ApiResponse;
import com.isums.scheduleservice.domains.dtos.ApiResponses;
import com.isums.scheduleservice.domains.dtos.CreateScheduleTemplateRequest;
import com.isums.scheduleservice.domains.dtos.ScheduleTemplateDto;
import com.isums.scheduleservice.infrastructures.abstracts.ScheduleTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedules/templates")
@RequiredArgsConstructor
public class ScheduleTemplateController {
    private final ScheduleTemplateService scheduleTemplateService;

    @PostMapping
    public ApiResponse<ScheduleTemplateDto> createTemplate(@RequestBody CreateScheduleTemplateRequest req){
        ScheduleTemplateDto res = scheduleTemplateService.createTemplate(req);
        return ApiResponses.created(res,"Create template successfully");
    }

    @GetMapping
    public ApiResponse<List<ScheduleTemplateDto>> getAllTemplate(){
        List<ScheduleTemplateDto> res = scheduleTemplateService.getAllTemplate();
        return ApiResponses.ok(res,"Get all template");
    }

    @GetMapping("/current/{date}")
    public ApiResponse<ScheduleTemplateDto> getCurrentTemplate(@PathVariable LocalDate date){
        ScheduleTemplateDto res = scheduleTemplateService.getCurrentTemplate(date);
        return ApiResponses.ok(res,"Get current template");
    }

}
