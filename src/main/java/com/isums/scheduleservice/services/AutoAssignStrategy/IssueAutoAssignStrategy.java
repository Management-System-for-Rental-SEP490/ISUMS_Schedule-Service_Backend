package com.isums.scheduleservice.services.AutoAssignStrategy;

import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.AssignmentType;
import com.isums.scheduleservice.domains.enums.JobAction;
import com.isums.scheduleservice.domains.enums.JobType;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.infrastructures.abstracts.AutoAssignStrategy;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import com.isums.scheduleservice.infrastructures.grpcs.HousesClientsGrpc;
import com.isums.scheduleservice.infrastructures.kafka.JobEventProducer;
import com.isums.scheduleservice.infrastructures.repositories.WorkSlotRepository;
import com.isums.scheduleservice.services.StaffAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueAutoAssignStrategy implements AutoAssignStrategy {

    private final WorkSlotRepository workSlotRepository;
    private final HousesClientsGrpc houseClient;
    private final JobEventProducer jobEventProducer;
    private final StaffAssignmentService staffAssignmentService;

    @Override
    public boolean supports(String referenceType) {
        return "ISSUE".equals(referenceType);
    }

    @Override
    public void handle(JobEvent event) {

        UUID jobId = event.getReferenceId();
        boolean exists = workSlotRepository.existsByJobIdAndStatusIn(jobId, List.of(SlotStatus.PENDING, SlotStatus.BOOKED, SlotStatus.NEED_RESCHEDULE));
        if (exists) {
            return;
        }
        UUID regionId = houseClient.getRegionByHouseId(event.getHouseId());
        List<UUID> staffIds = houseClient.getStaffIdsByRegion(regionId);

        if (staffIds.isEmpty()) {
            throw new RuntimeException("No staff available");
        }

        UUID staffId = staffAssignmentService.pickStaffWithoutTime(staffIds, regionId);

        WorkSlot slot = WorkSlot.builder()
                .jobId(event.getReferenceId())
                .staffId(staffId)
                .regionId(regionId)
                .jobType(JobType.ISSUE)
                .status(SlotStatus.PENDING)
                .assignmentType(AssignmentType.AUTO)
                .createdAt(Instant.now())
                .build();

        WorkSlot saved = workSlotRepository.save(slot);

        JobEvent assignedEvent = JobEvent.builder()
                .referenceId(event.getReferenceId())
                .houseId(event.getHouseId())
                .slotId(saved.getId())
                .staffId(staffId)
                .referenceType("ISSUE")
                .action(JobAction.JOB_ASSIGNED)
                .build();

        jobEventProducer.publishJobAssigned(assignedEvent);
    }

}
