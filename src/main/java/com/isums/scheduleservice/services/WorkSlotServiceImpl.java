package com.isums.scheduleservice.services;

import com.isums.scheduleservice.domains.dtos.*;
import com.isums.scheduleservice.domains.entities.ScheduleTemplate;
import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.AssignmentType;
import com.isums.scheduleservice.domains.enums.JobAction;
import com.isums.scheduleservice.domains.enums.JobType;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.domains.events.JobRescheduledEvent;
import com.isums.scheduleservice.domains.events.JobScheduledEvent;
import com.isums.scheduleservice.domains.events.SlotEvent;
import com.isums.scheduleservice.exceptions.BadRequestException;
import com.isums.scheduleservice.infrastructures.RoundRobin.RedisRoundRobinService;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import com.isums.scheduleservice.infrastructures.grpcs.HousesClientsGrpc;
import com.isums.scheduleservice.infrastructures.grpcs.IssueClientGrpc;
import com.isums.scheduleservice.infrastructures.grpcs.MaintenanceClientsGrpc;
import com.isums.scheduleservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.scheduleservice.infrastructures.kafka.JobEventProducer;
import com.isums.scheduleservice.infrastructures.mapper.ScheduleMapper;
import com.isums.scheduleservice.infrastructures.repositories.ScheduleTemplateRepository;
import com.isums.scheduleservice.infrastructures.repositories.WorkSlotRepository;
import com.isums.userservice.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Transactional
@Service
@RequiredArgsConstructor
public class WorkSlotServiceImpl implements WorkSlotService {
    private final WorkSlotRepository workSlotRepository;
    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final ScheduleMapper scheduleMapper;
    private final JobEventProducer jobEventProducer;
    private final UserClientsGrpc userClientsGrpc;
    private final HousesClientsGrpc houseClient;
    private final StaffAssignmentService staffAssignmentService;
    private final MaintenanceClientsGrpc maintenanceClient;
    private final IssueClientGrpc issueClient;

    @Override
    public WorkSlotDto manualAssign(ManualAssignRequest req) {
        try{

            boolean exists  = workSlotRepository.existsByJobIdAndStatusIn(
                    req.jobId(),
                    List.of(SlotStatus.BOOKED)
            );

            if (exists) {
                throw new RuntimeException("Job already scheduled");
            }

            WorkSlot slot = workSlotRepository.findFirstByJobIdAndStatusInOrderByCreatedAtDesc(
                            req.jobId(),
                            List.of(SlotStatus.PENDING, SlotStatus.NEED_RESCHEDULE)).orElse(null);

            ScheduleTemplate template = scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(req.startTime().toLocalDate())
                    .orElseThrow(() -> new RuntimeException("Template not found"));

            LocalDateTime endTime = req.startTime().plusMinutes(template.getSlotMinutes());

            validateWorkingHours(req.startTime(), endTime, template);

            List<WorkSlot> conflicts = workSlotRepository.findOverlappingSlots(
                    req.staffId(),
                    req.startTime(),
                    endTime
            );

            if (!conflicts.isEmpty()) {
                throw new RuntimeException("Staff already has job in this time");
            }


            if (slot == null) {
                 slot = WorkSlot.builder()
                        .jobId(req.jobId())
                        .staffId(req.staffId())
                        .jobType(req.jobType())
                        .startTime(req.startTime())
                        .endTime(endTime)
                        .status(SlotStatus.BOOKED)
                        .assignmentType(AssignmentType.MANUAL)
                        .createdAt(Instant.now())
                        .build();
            } else {
                slot.setStaffId(req.staffId());
                slot.setStartTime(req.startTime());
                slot.setEndTime(endTime);
                slot.setStatus(SlotStatus.BOOKED);
                slot.setAssignmentType(AssignmentType.MANUAL);
                slot.setJobType(req.jobType());
            }

            WorkSlot saved = workSlotRepository.save(slot);

            JobScheduledEvent event = new JobScheduledEvent();
            event.setReferenceId(saved.getJobId());
            event.setReferenceType(saved.getJobType().name());
            event.setSlotId(saved.getId());
            event.setStaffId(saved.getStaffId());
            event.setStartTime(saved.getStartTime());
            event.setEndTime(saved.getEndTime());
            event.setAction(JobAction.JOB_SCHEDULED);

            jobEventProducer.publishJobScheduled(event);

            return scheduleMapper.slot(saved);


        } catch (Exception ex) {
            throw new RuntimeException("Can't create work slot" + ex.getMessage());
        }
    }

    @Override
    public WorkSlotDto confirmSlot(ConfirmSlotRequest req) {
        try {

            WorkSlot slot = workSlotRepository.findByJobId(req.jobId())
                    .orElseThrow(() -> new RuntimeException("Slot not found"));

            if (slot.getStatus() != SlotStatus.PENDING
                    && slot.getStatus() != SlotStatus.NEED_RESCHEDULE) {
                throw new BadRequestException("Slot already confirmed");
            }

            UUID houseId = maintenanceClient.getHouseByJobId(req.jobId());
            UUID regionId = houseClient.getRegionByHouseId(houseId);
            List<UUID> staffIds = houseClient.getStaffIdsByRegion(regionId);

            if (staffIds.isEmpty()) {
                throw new RuntimeException("No staff in region");
            }

            ScheduleTemplate template = scheduleTemplateRepository
                    .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                            req.startTime().toLocalDate()
                    )
                    .orElseThrow(() -> new RuntimeException("Template not found"));

            LocalDateTime endTime = req.startTime().plusMinutes(template.getSlotMinutes());

            validateWorkingHours(req.startTime(), endTime, template);

            UUID staffId = staffAssignmentService.pickStaff(staffIds, regionId, req.startTime(), endTime);

            List<WorkSlot> conflicts = workSlotRepository.findOverlappingSlots(
                    staffId,
                    req.startTime(),
                    endTime
            );

            if (!conflicts.isEmpty()) {
                throw new RuntimeException("Selected staff already has schedule");
            }

            slot.setStaffId(staffId);
            slot.setRegionId(regionId);
            slot.setStartTime(req.startTime());
            slot.setEndTime(endTime);
            slot.setStatus(SlotStatus.BOOKED);
            slot.setAssignmentType(AssignmentType.AUTO);
            slot.setUpdateAt(Instant.now());

            WorkSlot saved = workSlotRepository.save(slot);

            JobScheduledEvent event = new JobScheduledEvent();
            event.setReferenceId(saved.getJobId());
            event.setReferenceType(saved.getJobType().name());
            event.setSlotId(saved.getId());
            event.setStaffId(saved.getStaffId());
            event.setStartTime(saved.getStartTime());
            event.setEndTime(saved.getEndTime());
            event.setAction(JobAction.JOB_SCHEDULED);

            jobEventProducer.publishJobScheduled(event);

            return scheduleMapper.slot(saved);

        } catch (Exception ex) {
            throw new RuntimeException("Cannot confirm slot: " + ex.getMessage(), ex);
        }
    }

    @Override
    public WorkSlotDto staffConfirmTime(ConfirmSlotRequest req) {
        try{
            WorkSlot slot = workSlotRepository.findByJobId(req.jobId()).orElseThrow(() -> new RuntimeException("Slot not found"));

            if (slot.getJobType() != JobType.ISSUE) {
                throw new RuntimeException("Only ISSUE jobs can be confirmed. Maintenance/Inspection not allowed");
            }

            if(slot.getStatus() != SlotStatus.PENDING
                    && slot.getStatus() != SlotStatus.WAITING_MANAGER_CONFIRM){
                throw new RuntimeException("Cannot update slot at this stage");
            }

            ScheduleTemplate template = scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(req.startTime().toLocalDate())
                    .orElseThrow(() -> new RuntimeException("Template not found"));

            LocalDateTime endTime = req.startTime().plusMinutes(template.getSlotMinutes());
            validateWorkingHours(req.startTime(),endTime,template);

            slot.setStartTime(req.startTime());
            slot.setEndTime(endTime);
            slot.setStatus(SlotStatus.WAITING_MANAGER_CONFIRM);

            WorkSlot save = workSlotRepository.save(slot);

            JobEvent event = JobEvent.builder()
                    .referenceId(save.getJobId())
                    .slotId(save.getId())
                    .staffId(save.getStaffId())
                    .referenceType(save.getJobType().name())
                    .startTime(save.getStartTime())
                    .endTime(save.getEndTime())
                    .action(JobAction.JOB_WAITING_MANAGER_CONFIRM)
                    .build();

            jobEventProducer.publishJobWaitingConfirm(event);


            return scheduleMapper.slot(save);
        } catch (Exception ex) {
            throw new RuntimeException("Cannot confirm slot: " + ex.getMessage(), ex);
        }
    }

    @Override
    public WorkSlotDto confirmSlotForStaff(UUID jobId) {
        try{
            WorkSlot slot = workSlotRepository.findByJobId(jobId).orElseThrow(() -> new RuntimeException("Slot not found"));

            if(slot.getStatus() != SlotStatus.WAITING_MANAGER_CONFIRM){
                throw new RuntimeException("Slot not waiting confirm");
            }

            slot.setStatus(SlotStatus.BOOKED);

            WorkSlot saved = workSlotRepository.save(slot);

            JobScheduledEvent event = new JobScheduledEvent();
            event.setReferenceId(saved.getJobId());
            event.setReferenceType(saved.getJobType().name());
            event.setSlotId(saved.getId());
            event.setStaffId(saved.getStaffId());
            event.setStartTime(saved.getStartTime());
            event.setEndTime(saved.getEndTime());
            event.setAction(JobAction.JOB_SCHEDULED);

            jobEventProducer.publishJobScheduled(event);

            return scheduleMapper.slot(saved);

        } catch (Exception ex) {
            throw new RuntimeException("Cannot confirm slot: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<WorkSlotDto> getSlotsByStaffId(String staffId) {
        try {
            UserResponse user = userClientsGrpc.getUserIdAndRoleByKeyCloakId(staffId);
            List<WorkSlot> slots = workSlotRepository.findByStaffIdOrderByStartTimeAsc(UUID.fromString(user.getId()));
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
    public WorkSlotDto getSlotById(UUID workSlotId) {
        try{
            WorkSlot slot = workSlotRepository.findById(workSlotId)
                    .orElseThrow(() -> new RuntimeException("Slot not found"));

            return scheduleMapper.slot(slot);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get work slot " + ex.getMessage());
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

            List<WorkSlot> conflicts = workSlotRepository.findOverlappingSlots(request.newStaffId(), request.newStartTime(), endTime);

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
            event.setReferenceId(request.jobId());
            event.setReferenceType(request.jobType().name());
            event.setSlotId(newSlot.getId());
            event.setStaffId(newSlot.getStaffId());
            event.setStartTime(newSlot.getStartTime());
            event.setEndTime(newSlot.getEndTime());
            event.setAction(JobAction.JOB_RESCHEDULED);

            jobEventProducer.publishJobRescheduled(event);

            return scheduleMapper.slot(newSlot);

        }catch (Exception ex){
            throw new RuntimeException("Can't reschedule slot " + ex.getMessage());
        }
    }

    @Override
    public List<WorkSlotDto> getSlotsByRange(LocalDate start, LocalDate end) {
        try{
            if (start.isAfter(end)) {
                throw new RuntimeException("Start date must be before end date");
            }

            LocalDateTime startTime = start.atStartOfDay();

            LocalDateTime endTime = end.plusDays(1).atStartOfDay();

            List<WorkSlot> slots = workSlotRepository.findByStartTimeBetweenOrderByStartTimeAsc(startTime,endTime);

            return scheduleMapper.slots(slots);

        }catch (Exception ex){
            throw new RuntimeException("Can't get all slot in range " + ex.getMessage());
        }
    }

    @Override
    public void markSlotDone(JobEvent event) {
        WorkSlot slot = workSlotRepository.findById(event.getSlotId())
                .orElseThrow();

        if (slot.getStatus() == SlotStatus.DONE) {
            return;
        }

        slot.setStatus(SlotStatus.DONE);
        workSlotRepository.save(slot);

        if (slot.getJobType() == JobType.INSPECTION) {
            jobEventProducer.publishJobCompleted(JobEvent.builder()
                    .referenceId(slot.getJobId())
                    .slotId(slot.getId())
                    .staffId(slot.getStaffId())
                    .referenceType("INSPECTION")
                    .action(JobAction.JOB_COMPLETED)
                    .build());
        }
    }

    @Override
    public List<DaySlotDto> getSlotsByDate(UUID jobId, LocalDate date) {
        try {
            UUID houseId = maintenanceClient.getHouseByJobId(jobId);
            UUID regionId = houseClient.getRegionByHouseId(houseId);
            List<UUID> staffIds = houseClient.getStaffIdsByRegion(regionId);

            if (staffIds == null || staffIds.isEmpty()) {
                throw new RuntimeException("No staff in region");
            }

            int totalStaff = staffIds.size();

            ScheduleTemplate template = scheduleTemplateRepository
                    .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date)
                    .orElseThrow(() -> new RuntimeException("Template not found"));

            LocalDateTime startDay = date.atTime(template.getOpenTime());
            LocalDateTime endDay = date.atTime(template.getCloseTime());

            List<WorkSlot> busySlots = workSlotRepository.findOverlappingSlotsForStaffs(staffIds, startDay, endDay );

            List<SlotsAvailableDto> slots = new ArrayList<>();

            LocalDateTime cur = startDay;

            while (cur.isBefore(endDay)) {

                LocalDateTime slotEnd  = cur.plusMinutes(template.getSlotMinutes());
                if (slotEnd .isAfter(endDay)) break;

                boolean overlapBreak =
                        cur.toLocalTime().isBefore(template.getBreakEnd()) &&
                                slotEnd .toLocalTime().isAfter(template.getBreakStart());

                if (overlapBreak) {
                    cur = template.getBreakEnd().atDate(date);
                    continue;
                }

                validateWorkingHours(cur, slotEnd, template);

                LocalDateTime slotStart = cur;
                Set<UUID> busyStaff = busySlots.stream()
                        .filter(s ->
                                s.getStartTime().isBefore(slotEnd) &&
                                        s.getEndTime().isAfter(slotStart)
                        )
                        .map(WorkSlot::getStaffId)
                        .collect(Collectors.toSet());

                int available = totalStaff - busyStaff.size();

                slots.add(new SlotsAvailableDto(
                        cur.toLocalTime(),
                        slotEnd.toLocalTime(),
                        available > 0 ? "AVAILABLE" : "UNAVAILABLE",
                        available
                ));

                cur = slotEnd.plusMinutes(template.getBufferMinutes());
            }

            return List.of(new DaySlotDto(date, slots));

        } catch (Exception ex) {
            throw new RuntimeException("Can't get slots by date: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<StaffDto> getAvailableStaff(UUID jobId, LocalDate date, LocalTime startTime) {
        WorkSlot slot = workSlotRepository.findByJobId(jobId)
                .orElseThrow(() -> new RuntimeException("Slot not found"));

        if (slot.getStatus() == SlotStatus.BOOKED && slot.getStaffId() != null) {
            throw new RuntimeException("Slot already has assigned staff");
        }
        UUID houseId = resolveHouseId(jobId);
        UUID regionId = houseClient.getRegionByHouseId(houseId);

        List<UUID> staffIds = houseClient.getStaffIdsByRegion(regionId);

        if (staffIds == null || staffIds.isEmpty()) {
            return List.of();
        }
        ScheduleTemplate template = scheduleTemplateRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date)
                .orElseThrow();

        LocalDateTime start = LocalDateTime.of(date, startTime);
        LocalDateTime end = start.plusMinutes(template.getSlotMinutes());

        List<WorkSlot> conflicts = workSlotRepository.findOverlappingSlotsForStaffs(staffIds, start, end);

        Set<UUID> busyStaff = conflicts.stream()
                .map(WorkSlot::getStaffId)
                .collect(Collectors.toSet());

        List<UUID> availableStaffIds = staffIds.stream()
                .filter(id -> !busyStaff.contains(id))
                .toList();

        if (availableStaffIds.isEmpty()) {
            return List.of();
        }

        List<UserResponse> users = availableStaffIds.stream()
                .map(id -> userClientsGrpc.getUser(id.toString()))
                .toList();

        return users.stream()
                .map(u -> new StaffDto(
                        UUID.fromString(u.getId()),
                        new UserDto(
                                UUID.fromString(u.getId()),
                                u.getName(),
                                u.getEmail(),
                                u.getPhoneNumber()
                        )
                ))
                .toList();
    }

    @Override
    public List<DaySlotDto> getMyAvailableSlotsRange(String staffId, LocalDate start, LocalDate end) {
        try {

            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.plusDays(1).atStartOfDay();

            List<WorkSlot> allSlots = workSlotRepository.findAllInRange(
                    UUID.fromString(staffId),
                    startDateTime,
                    endDateTime
            );

            Map<LocalDate, Set<LocalTime>> busyMap = new HashMap<>();

            for (WorkSlot slot : allSlots) {
                LocalDate date = slot.getStartTime().toLocalDate();
                LocalTime time = slot.getStartTime().toLocalTime();

                busyMap
                        .computeIfAbsent(date, k -> new HashSet<>())
                        .add(time);
            }

            List<DaySlotDto> result = new ArrayList<>();

            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {

                ScheduleTemplate template = scheduleTemplateRepository
                        .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date)
                        .orElseThrow(() -> new RuntimeException("Template not found"));

                LocalDateTime cur = date.atTime(template.getOpenTime());
                LocalDateTime endDay = date.atTime(template.getCloseTime());

                List<SlotsAvailableDto> slots = new ArrayList<>();

                while (cur.isBefore(endDay)) {

                    LocalDateTime slotEnd = cur.plusMinutes(template.getSlotMinutes());
                    if (slotEnd.isAfter(endDay)) break;

                    boolean overlapBreak =
                            cur.toLocalTime().isBefore(template.getBreakEnd()) &&
                                    slotEnd.toLocalTime().isAfter(template.getBreakStart());

                    if (overlapBreak) {
                        cur = template.getBreakEnd().atDate(date);
                        continue;
                    }

                    validateWorkingHours(cur, slotEnd, template);

                    boolean isBusy = busyMap
                            .getOrDefault(date, Set.of())
                            .contains(cur.toLocalTime());

                    slots.add(new SlotsAvailableDto(
                            cur.toLocalTime(),
                            slotEnd.toLocalTime(),
                            isBusy ? "UNAVAILABLE" : "AVAILABLE",
                            isBusy ? 0 : 1
                    ));

                    cur = slotEnd.plusMinutes(template.getBufferMinutes());
                }

                result.add(new DaySlotDto(date, slots));
            }

            return result;

        } catch (Exception ex) {
            throw new RuntimeException("Can't get slots: " + ex.getMessage(), ex);
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

    private UUID resolveHouseId(UUID jobId) {
        try {
            return maintenanceClient.getHouseByJobId(jobId);
        } catch (Exception ex1) {
            try {
                return issueClient.getHouseByJobId(jobId);
            } catch (Exception ex2) {
                throw new RuntimeException("Job not found in both maintenance & issue: " + jobId,ex2);
            }
        }
    }
}

