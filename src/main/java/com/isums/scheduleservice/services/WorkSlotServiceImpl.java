package com.isums.scheduleservice.services;

import com.isums.scheduleservice.domains.dtos.*;
import com.isums.scheduleservice.domains.entities.ScheduleTemplate;
import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.domains.events.JobRescheduledEvent;
import com.isums.scheduleservice.domains.events.JobScheduledEvent;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import com.isums.scheduleservice.infrastructures.kafka.JobEventProducer;
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
    private final JobEventProducer jobEventProducer;


    @Override
    public WorkSlotDto createSlots(CreateWorkSlotRequest req) {
        try {
            ScheduleTemplate template = scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(req.startTime().toLocalDate())
                    .orElseThrow(() -> new RuntimeException("Current template not found"));
            LocalDateTime endTime = req.startTime().plusMinutes(template.getSlotMinutes());

            validateWorkingHours(req.startTime(), endTime, template);

            List<WorkSlot> conflicts = workSlotRepository.findOverlappingSlots(req.staffId(), req.startTime(), endTime);

            if (!conflicts.isEmpty()) {
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

                JobScheduledEvent event = new JobScheduledEvent();
                event.setJobId(req.jobId());
                event.setJobType(req.jobType().name());
                event.setSlotId(slot.getId());
                event.setStaffId(slot.getStaffId());
                event.setStartTime(slot.getStartTime());
                event.setEndTime(slot.getEndTime());

                jobEventProducer.publishJobScheduled(event);



            return scheduleMapper.slot(slot);
        } catch (Exception ex) {
            throw new RuntimeException("Can't create work slot" + ex.getMessage());
        }
    }

    @Override
    public List<WorkSlotDto> getSlotsByStaffId(UUID staffId) {
        try {
            List<WorkSlot> slots = workSlotRepository.findByStaffIdOrderByStartTimeAsc(staffId);

            return scheduleMapper.slots(slots);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get work slot " + ex.getMessage());
        }
    }

    @Override
    public List<WorkSlotDto> getSlotsByDate(LocalDate date) {
        try {
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);

            List<WorkSlot> slots = workSlotRepository.findByStartTimeBetweenOrderByStartTimeAsc(start, end);

            return scheduleMapper.slots(slots);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get work slot " + ex.getMessage());
        }
    }

    @Override
    public Boolean cancelSlot(UUID slotId) {
        try {
            WorkSlot slot = workSlotRepository.findById(slotId)
                    .orElseThrow(() -> new IllegalArgumentException("Slot not found"));

            if (slot.getStatus() == SlotStatus.CANCELLED) {
                throw new IllegalArgumentException("Slot already cancelled");
            }

            slot.setStatus(SlotStatus.CANCELLED);

            workSlotRepository.save(slot);
            return true;
        } catch (Exception ex) {
            throw new RuntimeException("Can't update status work slot " + ex.getMessage());
        }
    }

    @Override
    public WorkSlotDto rescheduleSlot(RescheduleSlotRequest request) {
        try{
            WorkSlot oldSlot = workSlotRepository.findByJobIdAndStatus(request.jobId(),SlotStatus.NEED_RESCHEDULE)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No slot need reschedule"));

            ScheduleTemplate template = scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(request.newStartTime().toLocalDate())
                    .orElseThrow(() -> new RuntimeException("Current template not found"));

            LocalDateTime endTime = request.newStartTime().plusMinutes(template.getSlotMinutes());
            validateWorkingHours(request.newStartTime(),endTime,template);

            List<WorkSlot> conflicts =
                    workSlotRepository.findOverlappingSlots(
                            request.newStaffId(),
                            request.newStartTime(),
                            endTime
                    );

            if(!conflicts.isEmpty()){
                throw new RuntimeException("Staff already has job in this time");
            }

            WorkSlot newSlot = WorkSlot.builder()
                            .staffId(request.newStaffId())
                            .jobId(request.jobId())
                            .jobType(request.jobType())
                            .startTime(request.newStartTime())
                            .endTime(endTime)
                            .status(SlotStatus.BOOKED)
                            .createdAt(Instant.now())
                            .build();

            workSlotRepository.save(newSlot);

            oldSlot.setStatus(SlotStatus.CANCELLED);
            workSlotRepository.save(oldSlot);

            JobRescheduledEvent event = new JobRescheduledEvent();
            event.setJobId(request.jobId());
            event.setJobType(request.jobType().name());
            event.setSlotId(newSlot.getId());
            event.setStaffId(newSlot.getStaffId());
            event.setStartTime(newSlot.getStartTime());
            event.setEndTime(newSlot.getEndTime());

            jobEventProducer.publishJobRescheduled(event);

            return scheduleMapper.slot(newSlot);

        }catch (Exception ex){
            throw new RuntimeException("Can't reschedule slot " + ex.getMessage());
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

