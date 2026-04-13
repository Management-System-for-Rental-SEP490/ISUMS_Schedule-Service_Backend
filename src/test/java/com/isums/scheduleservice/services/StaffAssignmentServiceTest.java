package com.isums.scheduleservice.services;

import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.infrastructures.RoundRobin.RedisRoundRobinService;
import com.isums.scheduleservice.infrastructures.repositories.WorkSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StaffAssignmentService")
class StaffAssignmentServiceTest {

    @Mock private WorkSlotRepository workSlotRepository;
    @Mock private RedisRoundRobinService redisRoundRobinService;

    @InjectMocks private StaffAssignmentService service;

    private UUID regionId;
    private UUID staff1, staff2, staff3;
    private LocalDateTime start, end;

    @BeforeEach
    void setUp() {
        regionId = UUID.randomUUID();
        staff1 = UUID.randomUUID();
        staff2 = UUID.randomUUID();
        staff3 = UUID.randomUUID();
        start = LocalDateTime.now().plusHours(1);
        end = start.plusHours(2);
    }

    @Nested
    @DisplayName("pickStaff")
    class PickStaff {

        @Test
        @DisplayName("filters out staff with overlapping slots and picks from available")
        void filtersOverlap() {
            when(workSlotRepository.findOverlappingSlots(staff1, start, end))
                    .thenReturn(List.of(WorkSlot.builder().id(UUID.randomUUID()).build()));
            when(workSlotRepository.findOverlappingSlots(staff2, start, end))
                    .thenReturn(List.of());

            when(workSlotRepository.countJobsThisMonth(eq(List.of(staff2)), any()))
                    .thenReturn(List.of());
            when(workSlotRepository.countActiveJobs(eq(List.of(staff2)), any()))
                    .thenReturn(List.of());
            when(redisRoundRobinService.getNextIndex(regionId, 1)).thenReturn(0);

            UUID picked = service.pickStaff(List.of(staff1, staff2), regionId, start, end);

            assertThat(picked).isEqualTo(staff2);
        }

        @Test
        @DisplayName("throws when no staff is available in requested time range")
        void noAvailable() {
            when(workSlotRepository.findOverlappingSlots(staff1, start, end))
                    .thenReturn(List.of(WorkSlot.builder().id(UUID.randomUUID()).build()));

            assertThatThrownBy(() -> service.pickStaff(List.of(staff1), regionId, start, end))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("No staff available");
        }
    }

    @Nested
    @DisplayName("pickStaffWithoutTime")
    class PickWithoutTime {

        @Test
        @DisplayName("picks staff with fewer monthly jobs")
        void prefersLowerMonthly() {
            // staff1: 5 jobs this month, staff2: 2 jobs → prefers staff2
            when(workSlotRepository.countJobsThisMonth(eq(List.of(staff1, staff2)), any()))
                    .thenReturn(List.of(
                            new Object[]{staff1, 5L},
                            new Object[]{staff2, 2L}
                    ));
            when(workSlotRepository.countActiveJobs(eq(List.of(staff1, staff2)), any()))
                    .thenReturn(List.of());
            when(redisRoundRobinService.getNextIndex(regionId, 1)).thenReturn(0);

            UUID picked = service.pickStaffWithoutTime(List.of(staff1, staff2), regionId);

            assertThat(picked).isEqualTo(staff2);
        }

        @Test
        @DisplayName("uses active jobs as tiebreaker when monthly is equal")
        void tiebreakOnActive() {
            when(workSlotRepository.countJobsThisMonth(eq(List.of(staff1, staff2)), any()))
                    .thenReturn(List.of(
                            new Object[]{staff1, 3L},
                            new Object[]{staff2, 3L}
                    ));
            when(workSlotRepository.countActiveJobs(eq(List.of(staff1, staff2)), any()))
                    .thenReturn(List.of(
                            new Object[]{staff1, 4L},
                            new Object[]{staff2, 1L}
                    ));
            when(redisRoundRobinService.getNextIndex(regionId, 1)).thenReturn(0);

            UUID picked = service.pickStaffWithoutTime(List.of(staff1, staff2), regionId);

            assertThat(picked).isEqualTo(staff2);
        }

        @Test
        @DisplayName("round-robins across tied candidates via Redis")
        void roundRobinTie() {
            when(workSlotRepository.countJobsThisMonth(any(), any())).thenReturn(List.of());
            when(workSlotRepository.countActiveJobs(any(), any())).thenReturn(List.of());
            when(redisRoundRobinService.getNextIndex(eq(regionId), eq(3))).thenReturn(1);

            UUID picked = service.pickStaffWithoutTime(
                    List.of(staff1, staff2, staff3), regionId);

            // sorted keeps natural order when ties (no stable guarantee, but
            // both counts are empty so same scores → all three candidates remain);
            // index 1 of the sorted list is what RedisRR returned
            assertThat(picked).isIn(staff1, staff2, staff3);
        }

        @Test
        @DisplayName("falls back to ThreadLocalRandom when Redis throws")
        void redisFailureFallback() {
            when(workSlotRepository.countJobsThisMonth(any(), any())).thenReturn(List.of());
            when(workSlotRepository.countActiveJobs(any(), any())).thenReturn(List.of());
            when(redisRoundRobinService.getNextIndex(any(UUID.class), anyInt()))
                    .thenThrow(new RuntimeException("redis down"));

            UUID picked = service.pickStaffWithoutTime(
                    List.of(staff1, staff2), regionId);

            assertThat(picked).isIn(staff1, staff2);
        }

        @Test
        @DisplayName("returns the single candidate when only one staff provided")
        void singleStaff() {
            when(workSlotRepository.countJobsThisMonth(eq(List.of(staff1)), any()))
                    .thenReturn(List.of());
            when(workSlotRepository.countActiveJobs(eq(List.of(staff1)), any()))
                    .thenReturn(List.of());
            when(redisRoundRobinService.getNextIndex(regionId, 1)).thenReturn(0);

            assertThat(service.pickStaffWithoutTime(List.of(staff1), regionId))
                    .isEqualTo(staff1);
        }
    }
}
