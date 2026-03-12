package com.isums.scheduleservice.services;

import com.isums.scheduleservice.domains.dtos.*;
import com.isums.scheduleservice.domains.entities.ScheduleTemplate;
import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import com.isums.scheduleservice.infrastructures.mapper.ScheduleMapper;
import com.isums.scheduleservice.infrastructures.repositories.ScheduleTemplateRepository;
import com.isums.scheduleservice.infrastructures.repositories.WorkSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Transactional
@Service
@RequiredArgsConstructor
public class WorkSlotServiceImpl implements WorkSlotService {
    private final WorkSlotRepository workSlotRepository;
    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final ScheduleMapper scheduleMapper;


    @Override
    public WorkSlotDto createSlots(CreateWorkSlotRequest req) {
        try{
            ScheduleTemplate template = scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(req.startTime().toLocalDate())
                    .orElseThrow(() -> new RuntimeException("Current template not found"));
            LocalDateTime endTime = req.startTime().plusMinutes(template.getSlotMinutes());

            validateWorkingHours(req.startTime(),endTime,template);

            List<WorkSlot> conflicts = workSlotRepository.findOverlappingSlots(req.staffId(),req.startTime(),endTime);

            if(!conflicts.isEmpty()){
                throw new RuntimeException("Staff already has job in this time");
            }

            WorkSlot slot = WorkSlot.builder()
                    .staffId(req.staffId())
                    .jobId(req.jobId())
                    .jobType(req.jobType())
                    .startTime((req.startTime()))
                    .endTime((endTime))
                    .status(SlotStatus.BOOKED)
                    .createdAt(Instant.now())
                    .build();

            workSlotRepository.save(slot);

            return scheduleMapper.slot(slot);
        } catch (Exception ex) {
            throw new RuntimeException("Can't create work slot" + ex.getMessage());
        }
    }

    private void validateWorkingHours(LocalDateTime start,LocalDateTime end ,ScheduleTemplate template){
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();

        boolean isMorning = !startTime.isBefore(template.getOpenTime())
                && !endTime.isAfter(template.getBreakStart());

        boolean isAfternoon = !startTime.isBefore(template.getBreakEnd())
                && !endTime.isAfter(template.getCloseTime());

        DayOfWeek day = start.getDayOfWeek();
        String shortenDay = day.name().substring(0,3);

        if(!template.getWorkingDays().contains(shortenDay)){
            throw new RuntimeException("Not a working day");
        }
        if(!isMorning && !isAfternoon){
            throw new RuntimeException("Outside working hours");
        }
    }
}

