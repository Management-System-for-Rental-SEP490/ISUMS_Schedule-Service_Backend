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
import com.isums.scheduleservice.services.WorkSlotServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class MaintenanceAutoAssignStrategy implements AutoAssignStrategy {
    private final WorkSlotRepository workSlotRepository;
    private final HousesClientsGrpc houseClient;
    private final JobEventProducer jobEventProducer;

    @Override
    public boolean supports(String referenceType) {
        return "MAINTENANCE".equals(referenceType);
    }

    @Override
    public void handle(JobEvent event) {
        UUID jobId = event.getReferenceId();
        boolean exists = workSlotRepository.existsByJobIdAndStatusIn(jobId,
                List.of(SlotStatus.PENDING, SlotStatus.BOOKED, SlotStatus.NEED_RESCHEDULE));

        if (exists) {
            return;
        }

        WorkSlot slot = WorkSlot.builder()
                .jobId(event.getReferenceId())
                .jobType(JobType.MAINTENANCE)
                .status(SlotStatus.PENDING)
                .assignmentType(AssignmentType.AUTO)
                .createdAt(Instant.now())
                .build();

        WorkSlot saved = workSlotRepository.save(slot);

        JobEvent assignedEvent = JobEvent.builder()
                .referenceId(jobId)
                .houseId(event.getHouseId())
                .slotId(saved.getId())
                .referenceType(event.getReferenceType())
                .action(JobAction.JOB_ASSIGNED)
                .build();

        jobEventProducer.publishJobAssigned(assignedEvent);
    }
}
