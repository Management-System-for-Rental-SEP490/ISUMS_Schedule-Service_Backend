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
import com.isums.scheduleservice.infrastructures.RoundRobin.RedisRoundRobinService;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import com.isums.scheduleservice.infrastructures.grpcs.HousesClientsGrpc;
import com.isums.scheduleservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.scheduleservice.infrastructures.kafka.JobEventProducer;
import com.isums.scheduleservice.infrastructures.mapper.ScheduleMapper;
import com.isums.scheduleservice.infrastructures.repositories.ScheduleTemplateRepository;
import com.isums.scheduleservice.infrastructures.repositories.WorkSlotRepository;
import com.isums.userservice.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import org.aspectj.weaver.patterns.ConcreteCflowPointcut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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
    private final RedisRoundRobinService redisRoundRobinService;


//    @Override
//    public WorkSlotDto createSlots(CreateWorkSlotRequest req) {
//        try {
//
//            boolean exists = workSlotRepository.existsByJobIdAndStatusIn(
//                    req.jobId(),
//                    List.of(SlotStatus.PENDING, SlotStatus.BOOKED, SlotStatus.NEED_RESCHEDULE)
//            );
//
//            if (exists) {
//                throw new RuntimeException("Job already has active slot");
//            }
//
//            ScheduleTemplate template = scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(req.startTime().toLocalDate())
//                    .orElseThrow(() -> new RuntimeException("Current template not found"));
//            LocalDateTime endTime = req.startTime().plusMinutes(template.getSlotMinutes());
//
//            validateWorkingHours(req.startTime(), endTime, template);
//
//            List<WorkSlot> conflicts = workSlotRepository.findOverlappingSlots(req.staffId(), req.startTime(), endTime);
//
//            if (!conflicts.isEmpty()) {
//                throw new RuntimeException("Staff already has job in this time");
//            }
//
//            WorkSlot slot = WorkSlot.builder()
//                    .staffId(req.staffId())
//                    .jobId(req.jobId())
//                    .jobType(req.jobType())
//                    .startTime((req.startTime()))
//                    .endTime((endTime))
//                    .status(SlotStatus.BOOKED)
//                    .assignmentType(AssignmentType.MANUAL)
//                    .createdAt(Instant.now())
//                    .build();
//
//            WorkSlot save = workSlotRepository.save(slot);
//
//                JobScheduledEvent event = new JobScheduledEvent();
//                event.setReferenceId(req.jobId());
//                event.setReferenceType(req.jobType().name());
//                event.setSlotId(save.getId());
//                event.setStaffId(save.getStaffId());
//                event.setStartTime(save.getStartTime());
//                event.setEndTime(save.getEndTime());
//                event.setAction(JobAction.JOB_SCHEDULED);
//
//                jobEventProducer.publishJobScheduled(event);
//
//            return scheduleMapper.slot(slot);
//        } catch (Exception ex) {
//            throw new RuntimeException("Can't create work slot" + ex.getMessage());
//        }
//    }

    @Override
    public WorkSlotDto manualAssign(ManualAssignRequest req) {
        try{
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

            
            slot.setStaffId(req.staffId());
            slot.setStartTime(req.startTime());
            slot.setEndTime(endTime);
            slot.setStatus(SlotStatus.BOOKED);
            slot.setAssignmentType(AssignmentType.MANUAL);

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
        try{
            WorkSlot slot = workSlotRepository.findByJobId(req.jobId())
                    .orElseThrow(() ->  new RuntimeException("Slot not found for job"));

            if(slot.getStatus() != SlotStatus.PENDING){
                throw new RuntimeException("Only pending status can be confirmed ");
            }

            ScheduleTemplate template = scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(req.startTime().toLocalDate())
                    .orElseThrow(() ->  new RuntimeException("Current template not found"));

            LocalDateTime endTime = req.startTime().plusMinutes(template.getSlotMinutes());

            validateWorkingHours(req.startTime(),endTime,template);

            List<WorkSlot> conflicts = workSlotRepository.findOverlappingSlots(slot.getStaffId(),req.startTime(),endTime);

            if(!conflicts.isEmpty()){
                throw new RuntimeException("Staff already has job in this time");
            }

            slot.setStartTime(req.startTime());
            slot.setEndTime(endTime);
            slot.setStatus(SlotStatus.BOOKED);
            slot.setUpdateAt(Instant.now());

            WorkSlot updatedSlot = workSlotRepository.save(slot);

            JobScheduledEvent event = new JobScheduledEvent();
            event.setReferenceId(updatedSlot.getJobId());
            event.setReferenceType(updatedSlot.getJobType().name());
            event.setSlotId(updatedSlot.getId());
            event.setStaffId(updatedSlot.getStaffId());
            event.setStartTime(updatedSlot.getStartTime());
            event.setEndTime(updatedSlot.getEndTime());
            event.setAction(JobAction.JOB_SCHEDULED);

            jobEventProducer.publishJobScheduled(event);

            return scheduleMapper.slot(updatedSlot);

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

//    @Override
//    public List<WorkSlotDto> getSlotsByDate(LocalDate date) {
//        try {
//            LocalDateTime start = date.atStartOfDay();
//            LocalDateTime end = date.atTime(LocalTime.MAX);
//
//            List<WorkSlot> slots = workSlotRepository.findByStartTimeBetweenOrderByStartTimeAsc(start, end);
//
//            return scheduleMapper.slots(slots);
//        } catch (Exception ex) {
//            throw new RuntimeException("Can't get work slot " + ex.getMessage());
//        }
//    }

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
    public List<DaySlotDto> generateSlots(LocalDate start, LocalDate end) {
        try{
            List<DaySlotDto> result = new ArrayList<>();
            for(LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)){
                ScheduleTemplate template = scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date)
                        .orElseThrow(() -> new RuntimeException("Template not found"));

                LocalDateTime startDay = date.atTime(template.getOpenTime());
                LocalDateTime endDay =  date.atTime(template.getCloseTime());

                List<WorkSlot> exist = workSlotRepository.findByStartTimeBetween(startDay,endDay);

                Set<LocalDateTime> existed = exist.stream().map(WorkSlot::getStartTime).collect(Collectors.toSet());

                List<SlotsAvailableDto> slots = new ArrayList<>();

                LocalDateTime cur = startDay;

                while(cur.isBefore(endDay)) {
                    LocalDateTime endSlot = cur.plusMinutes(template.getSlotMinutes());

                    if (endSlot.isAfter(endDay)) break;

                    LocalTime slotStart = cur.toLocalTime();
                    LocalTime slotEndTime = endSlot.toLocalTime();

                    boolean overlapBreak =
                            slotStart.isBefore(template.getBreakEnd()) &&
                                    slotEndTime.isAfter(template.getBreakStart());

                    if (overlapBreak) {
                        cur = template.getBreakEnd().atDate(date);
                        continue;
                    }

                    String status = existed.contains(cur)
                            ? "UNAVAILABLE"
                            : "AVAILABLE";

                    slots.add(new SlotsAvailableDto(
                            cur.toLocalTime(),
                            endSlot.toLocalTime(),
                            status
                    ));

                    cur = endSlot.plusMinutes(template.getBufferMinutes());
                }
                result.add(new DaySlotDto(date,slots));

                }
            return result;

        } catch (Exception ex) {
            throw new RuntimeException("Can't generate slot" + ex.getMessage());
        }
    }

    @Override
    public void markSlotDone(JobEvent event) {
        WorkSlot slot = workSlotRepository.findById(event.getSlotId())
                .orElseThrow();

        if(slot.getStatus() == SlotStatus.DONE){
            return;
        }

        slot.setStatus(SlotStatus.DONE);

        workSlotRepository.save(slot);
    }

    @Override
    public void handleAutoAssign(JobEvent event) {

        UUID jobId = event.getReferenceId();

        boolean exists = workSlotRepository.existsByJobIdAndStatusIn(jobId,
                List.of(SlotStatus.PENDING,SlotStatus.BOOKED,SlotStatus.NEED_RESCHEDULE));

        if (exists) {

            WorkSlot existing = workSlotRepository
                    .findFirstByJobIdAndStatusInOrderByCreatedAtDesc(
                            jobId,
                            List.of(SlotStatus.PENDING, SlotStatus.BOOKED, SlotStatus.NEED_RESCHEDULE)
                    ).orElseThrow(() -> new RuntimeException("Slot already exist"));

            JobEvent assignedEvent = JobEvent.builder()
                    .referenceId(jobId)
                    .houseId(event.getHouseId())
                    .slotId(existing.getId())
                    .staffId(existing.getStaffId())
                    .referenceType(event.getReferenceType())
                    .action(JobAction.JOB_ASSIGNED)
                    .build();

            jobEventProducer.publishJobAssigned(assignedEvent);
            return;
        }

        UUID regionId = houseClient.getRegionByHouseId(event.getHouseId());

        List<UUID> staffIds =houseClient.getStaffIdsByRegion(regionId);

        if (staffIds == null || staffIds.isEmpty()) {
            throw new RuntimeException("No staff available");
        }

        UUID staffId = pickStaff(staffIds, regionId);

        WorkSlot slot = WorkSlot.builder()
                .jobId(jobId)
                .staffId(staffId)
                .regionId(regionId)
                .jobType(JobType.valueOf(event.getReferenceType()))
                .status(SlotStatus.PENDING)
                .assignmentType(AssignmentType.AUTO)
                .createdAt(Instant.now())
                .build();

        WorkSlot save = workSlotRepository.save(slot);

        JobEvent assignedEvent = JobEvent.builder()
                .referenceId(jobId)
                .houseId(event.getHouseId())
                .slotId(save.getId())
                .staffId(staffId)
                .referenceType(event.getReferenceType())
                .action(JobAction.JOB_ASSIGNED)
                .build();

        jobEventProducer.publishJobAssigned(assignedEvent);
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

    private UUID pickStaff(List<UUID> staffIds, UUID regionId){

        Instant startOfMonth = LocalDate.now()
                .withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        Map<UUID,Long> monthlyCount = workSlotRepository.countJobsThisMonth(staffIds,startOfMonth)
                .stream()
                .collect(Collectors.toMap(
                        r -> (UUID) r[0],
                        r -> (Long) r[1]
                ));

        Map<UUID,Long> activeCount = workSlotRepository.countActiveJobs(staffIds,List.of(SlotStatus.PENDING,SlotStatus.BOOKED,SlotStatus.NEED_RESCHEDULE))
                .stream()
                .collect(Collectors.toMap(
                r -> (UUID) r[0],
                r -> (Long) r[1]
                ));

        List<StaffScore> scores = staffIds.stream().map(
                staffId -> new StaffScore(staffId,
                        monthlyCount.getOrDefault(staffId,0L),
                        activeCount.getOrDefault(staffId,0L)))
                .toList();

        List<StaffScore> sorted = scores.stream()
                .sorted(Comparator.comparingLong(StaffScore::monthlyJobs).thenComparingLong(StaffScore::activeJobs))
                .toList();

        StaffScore best = sorted.getFirst();

        List<StaffScore> candidates = sorted.stream()
                .filter(s ->
                        s.monthlyJobs() == best.monthlyJobs() &&
                        s.activeJobs() == best.activeJobs())
                .toList();

        return pickRoundRobin(candidates, regionId);
    }

    private UUID pickRoundRobin(List<StaffScore> candidates, UUID regionId) {
        try {
            int index = redisRoundRobinService.getNextIndex(regionId, candidates.size());
            return candidates.get(index).staffId();
        } catch (Exception ex) {
            // fallback nếu Redis lỗi
            int index = ThreadLocalRandom.current().nextInt(candidates.size());
            return candidates.get(index).staffId();
        }
    }
}

