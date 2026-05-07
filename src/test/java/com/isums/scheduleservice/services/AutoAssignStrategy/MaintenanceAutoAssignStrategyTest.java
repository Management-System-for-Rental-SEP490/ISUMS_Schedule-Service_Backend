package com.isums.scheduleservice.services.AutoAssignStrategy;

import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.AssignmentType;
import com.isums.scheduleservice.domains.enums.JobAction;
import com.isums.scheduleservice.domains.enums.JobType;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.infrastructures.grpcs.HousesClientsGrpc;
import com.isums.scheduleservice.infrastructures.kafka.JobEventProducer;
import com.isums.scheduleservice.infrastructures.repositories.WorkSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaintenanceAutoAssignStrategy")
class MaintenanceAutoAssignStrategyTest {

    @Mock private WorkSlotRepository workSlotRepository;
    @Mock private HousesClientsGrpc houseClient;
    @Mock private JobEventProducer jobEventProducer;

    @InjectMocks private MaintenanceAutoAssignStrategy strategy;

    private UUID jobId, houseId, slotId;
    private JobEvent event;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        houseId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        event = JobEvent.builder().referenceId(jobId).houseId(houseId)
                .referenceType("MAINTENANCE").action(JobAction.JOB_CREATED).build();
    }

    @Test
    @DisplayName("supports('MAINTENANCE') only")
    void supports() {
        assertThat(strategy.supports("MAINTENANCE")).isTrue();
        assertThat(strategy.supports("ISSUE")).isFalse();
        assertThat(strategy.supports("")).isFalse();
    }

    @Test
    @DisplayName("handle creates MAINTENANCE slot without staff assignment and publishes JOB_ASSIGNED")
    void happyPath() {
        when(workSlotRepository.existsByJobIdAndStatusIn(jobId,
                List.of(SlotStatus.PENDING, SlotStatus.BOOKED, SlotStatus.NEED_RESCHEDULE)))
                .thenReturn(false);
        when(workSlotRepository.save(any(WorkSlot.class))).thenAnswer(inv -> {
            WorkSlot s = inv.getArgument(0);
            s.setId(slotId);
            return s;
        });

        strategy.handle(event);

        ArgumentCaptor<WorkSlot> slotCap = ArgumentCaptor.forClass(WorkSlot.class);
        verify(workSlotRepository).save(slotCap.capture());
        WorkSlot saved = slotCap.getValue();
        assertThat(saved.getJobId()).isEqualTo(jobId);
        assertThat(saved.getJobType()).isEqualTo(JobType.MAINTENANCE);
        assertThat(saved.getStatus()).isEqualTo(SlotStatus.PENDING);
        assertThat(saved.getAssignmentType()).isEqualTo(AssignmentType.AUTO);
        // Maintenance does not pre-assign a staff
        assertThat(saved.getStaffId()).isNull();

        ArgumentCaptor<JobEvent> evtCap = ArgumentCaptor.forClass(JobEvent.class);
        verify(jobEventProducer).publishJobAssigned(evtCap.capture());
        assertThat(evtCap.getValue().getAction()).isEqualTo(JobAction.JOB_ASSIGNED);
        assertThat(evtCap.getValue().getReferenceType()).isEqualTo("MAINTENANCE");
        assertThat(evtCap.getValue().getSlotId()).isEqualTo(slotId);

        verifyNoInteractions(houseClient);
    }

    @Test
    @DisplayName("handle is idempotent — skips when existing active slot present")
    void alreadyExists() {
        when(workSlotRepository.existsByJobIdAndStatusIn(any(UUID.class), anyList())).thenReturn(true);

        strategy.handle(event);

        verify(workSlotRepository, never()).save(any());
        verify(jobEventProducer, never()).publishJobAssigned(any());
    }
}
