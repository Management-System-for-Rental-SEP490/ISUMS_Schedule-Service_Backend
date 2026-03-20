package com.isums.scheduleservice.infrastructures.mapper;

import com.isums.scheduleservice.domains.dtos.ScheduleTemplateDto;
import com.isums.scheduleservice.domains.dtos.WorkSlotDto;
import com.isums.scheduleservice.domains.entities.ScheduleTemplate;
import com.isums.scheduleservice.domains.entities.WorkSlot;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ScheduleMapper {
    ScheduleTemplateDto schedule(ScheduleTemplate scheduleTemplate);
    List<ScheduleTemplateDto> schedules(List<ScheduleTemplate> scheduleTemplates);

    WorkSlotDto slot(WorkSlot slot);
    List<WorkSlotDto> slots(List<WorkSlot> slots);

}
