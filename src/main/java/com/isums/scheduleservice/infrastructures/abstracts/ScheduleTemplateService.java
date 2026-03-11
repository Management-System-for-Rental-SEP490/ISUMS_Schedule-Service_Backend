package com.isums.scheduleservice.infrastructures.abstracts;

import com.isums.scheduleservice.domains.dtos.CreateScheduleTemplateRequest;
import com.isums.scheduleservice.domains.dtos.ScheduleTemplateDto;
import org.springframework.cglib.core.Local;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ScheduleTemplateService {
    ScheduleTemplateDto createTemplate(CreateScheduleTemplateRequest req);
    List<ScheduleTemplateDto> getAllTemplate();
    ScheduleTemplateDto getCurrentTemplate(LocalDate date);
}
