package com.isums.scheduleservice.infrastructures.mapper;

import com.isums.scheduleservice.domains.dtos.ScheduleTemplateDto;
import com.isums.scheduleservice.domains.dtos.SlotDto;
import com.isums.scheduleservice.domains.dtos.WorkSlotDto;
import com.isums.scheduleservice.domains.entities.ScheduleTemplate;
import com.isums.scheduleservice.domains.entities.WorkSlot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ScheduleMapper {
    ScheduleTemplateDto schedule(ScheduleTemplate scheduleTemplate);
    List<ScheduleTemplateDto> schedules(List<ScheduleTemplate> scheduleTemplates);

    SlotDto slot(WorkSlot workSlot);
    List<SlotDto> slots(List<WorkSlot> workSlots);

}
