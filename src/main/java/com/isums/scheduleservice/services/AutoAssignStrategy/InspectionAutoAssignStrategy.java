package com.isums.scheduleservice.services.AutoAssignStrategy;

import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.AssignmentType;
import com.isums.scheduleservice.domains.enums.JobAction;
import com.isums.scheduleservice.domains.enums.JobType;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.infrastructures.abstracts.AutoAssignStrategy;
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
public class InspectionAutoAssignStrategy implements AutoAssignStrategy {

    private final WorkSlotRepository workSlotRepository;
    private final HousesClientsGrpc houseClient;
    private final JobEventProducer jobEventProducer;
    private final StaffAssignmentService staffAssignmentService;

    @Override
    public boolean supports(String referenceType) {
        return "INSPECTION".equals(referenceType);
    }

    @Override
    public void handle(JobEvent event) {
        UUID jobId = event.getReferenceId();

        boolean exists = workSlotRepository.existsByJobIdAndStatusIn(
                jobId,
                List.of(SlotStatus.PENDING, SlotStatus.BOOKED, SlotStatus.NEED_RESCHEDULE)
        );
        if (exists) return;

        UUID regionId = houseClient.getRegionByHouseId(event.getHouseId());
        List<UUID> staffIds = houseClient.getStaffIdsByRegion(regionId);
        if (staffIds.isEmpty()) throw new RuntimeException("No staff in region");

        UUID staffId = staffAssignmentService.pickStaffWithoutTime(staffIds, regionId);

        WorkSlot slot = WorkSlot.builder()
                .jobId(jobId)
                .staffId(staffId)
                .regionId(regionId)
                .jobType(JobType.INSPECTION)
                .status(SlotStatus.PENDING)
                .assignmentType(AssignmentType.AUTO)
                .createdAt(Instant.now())
                .build();

        WorkSlot saved = workSlotRepository.save(slot);

        jobEventProducer.publishJobAssigned(JobEvent.builder()
                .referenceId(jobId)
                .houseId(event.getHouseId())
                .slotId(saved.getId())
                .staffId(staffId)
                .referenceType("INSPECTION")
                .action(JobAction.JOB_ASSIGNED)
                .build());
    }
}