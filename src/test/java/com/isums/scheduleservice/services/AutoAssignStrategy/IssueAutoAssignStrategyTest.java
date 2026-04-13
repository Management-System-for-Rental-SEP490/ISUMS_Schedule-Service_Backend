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
import com.isums.scheduleservice.services.StaffAssignmentService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueAutoAssignStrategy")
class IssueAutoAssignStrategyTest {

    @Mock private WorkSlotRepository workSlotRepository;
    @Mock private HousesClientsGrpc houseClient;
    @Mock private JobEventProducer jobEventProducer;
    @Mock private StaffAssignmentService staffAssignmentService;

    @InjectMocks private IssueAutoAssignStrategy strategy;

    private UUID jobId, houseId, regionId, staffId, slotId;
    private JobEvent event;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        houseId = UUID.randomUUID();
        regionId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        event = JobEvent.builder().referenceId(jobId).houseId(houseId)
                .referenceType("ISSUE").action(JobAction.JOB_CREATED).build();
    }

    @Test
    @DisplayName("supports('ISSUE') only")
    void supports() {
        assertThat(strategy.supports("ISSUE")).isTrue();
        assertThat(strategy.supports("INSPECTION")).isFalse();
        assertThat(strategy.supports(null)).isFalse();
    }

    @Test
    @DisplayName("handle creates slot, assigns staff and publishes JOB_ASSIGNED")
    void happyPath() {
        when(workSlotRepository.existsByJobIdAndStatusIn(jobId,
                List.of(SlotStatus.PENDING, SlotStatus.BOOKED, SlotStatus.NEED_RESCHEDULE)))
                .thenReturn(false);
        when(houseClient.getRegionByHouseId(houseId)).thenReturn(regionId);
        when(houseClient.getStaffIdsByRegion(regionId)).thenReturn(List.of(staffId));
        when(staffAssignmentService.pickStaffWithoutTime(List.of(staffId), regionId))
                .thenReturn(staffId);
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
        assertThat(saved.getStaffId()).isEqualTo(staffId);
        assertThat(saved.getRegionId()).isEqualTo(regionId);
        assertThat(saved.getJobType()).isEqualTo(JobType.ISSUE);
        assertThat(saved.getStatus()).isEqualTo(SlotStatus.PENDING);
        assertThat(saved.getAssignmentType()).isEqualTo(AssignmentType.AUTO);

        ArgumentCaptor<JobEvent> evtCap = ArgumentCaptor.forClass(JobEvent.class);
        verify(jobEventProducer).publishJobAssigned(evtCap.capture());
        JobEvent emitted = evtCap.getValue();
        assertThat(emitted.getSlotId()).isEqualTo(slotId);
        assertThat(emitted.getStaffId()).isEqualTo(staffId);
        assertThat(emitted.getAction()).isEqualTo(JobAction.JOB_ASSIGNED);
        assertThat(emitted.getReferenceType()).isEqualTo("ISSUE");
    }

    @Test
    @DisplayName("handle returns silently when active slot already exists (idempotent)")
    void alreadyExists() {
        when(workSlotRepository.existsByJobIdAndStatusIn(any(UUID.class), anyList())).thenReturn(true);

        strategy.handle(event);

        verify(houseClient, never()).getRegionByHouseId(any());
        verify(workSlotRepository, never()).save(any());
        verify(jobEventProducer, never()).publishJobAssigned(any());
    }

    @Test
    @DisplayName("handle throws when region has no staff")
    void noStaff() {
        when(workSlotRepository.existsByJobIdAndStatusIn(any(UUID.class), anyList())).thenReturn(false);
        when(houseClient.getRegionByHouseId(houseId)).thenReturn(regionId);
        when(houseClient.getStaffIdsByRegion(regionId)).thenReturn(List.of());

        assertThatThrownBy(() -> strategy.handle(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No staff");
        verify(workSlotRepository, never()).save(any());
    }
}
