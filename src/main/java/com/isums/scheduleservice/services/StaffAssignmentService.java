package com.isums.scheduleservice.services;

import com.isums.scheduleservice.domains.dtos.StaffScore;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.infrastructures.RoundRobin.RedisRoundRobinService;
import com.isums.scheduleservice.infrastructures.repositories.WorkSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffAssignmentService {

    private final WorkSlotRepository workSlotRepository;
    private final RedisRoundRobinService redisRoundRobinService;

    public UUID pickStaff(
            List<UUID> staffIds,
            UUID regionId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {

        List<UUID> available = staffIds.stream()
                .filter(s -> workSlotRepository
                        .findOverlappingSlots(s, startTime, endTime)
                        .isEmpty())
                .toList();

        if (available.isEmpty()) {
            throw new RuntimeException("No staff available in this time");
        }

        return pickStaffWithoutTime(available, regionId);
    }

    public UUID pickStaffWithoutTime(List<UUID> staffIds, UUID regionId) {

        Map<UUID, Long> monthlyCount = calculateMonthlyJobs(staffIds);

        Map<UUID, Long> activeCount = workSlotRepository
                .countActiveJobs(
                        staffIds,
                        List.of(SlotStatus.PENDING, SlotStatus.BOOKED, SlotStatus.NEED_RESCHEDULE)
                )
                .stream()
                .collect(Collectors.toMap(
                        r -> (UUID) r[0],
                        r -> (Long) r[1]
                ));

        List<StaffScore> sorted = staffIds.stream()
                .map(s -> new StaffScore(
                        s,
                        monthlyCount.getOrDefault(s, 0L),
                        activeCount.getOrDefault(s, 0L)
                ))
                .sorted(Comparator
                        .comparingLong(StaffScore::monthlyJobs)
                        .thenComparingLong(StaffScore::activeJobs))
                .toList();

        StaffScore best = sorted.getFirst();

        List<StaffScore> candidates = sorted.stream()
                .filter(s ->
                        s.monthlyJobs() == best.monthlyJobs() &&
                                s.activeJobs() == best.activeJobs()
                )
                .toList();

        return pickRoundRobin(candidates, regionId);
    }

    public Map<UUID, Long> calculateMonthlyJobs(List<UUID> staffIds) {
        if (staffIds == null || staffIds.isEmpty()) {
            return Map.of();
        }

        Instant startOfMonth = LocalDate.now()
                .withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        return workSlotRepository
                .countJobsThisMonth(staffIds, startOfMonth)
                .stream()
                .collect(Collectors.toMap(
                        r -> (UUID) r[0],
                        r -> ((Number) r[1]).longValue()
                ));
    }

    private UUID pickRoundRobin(List<StaffScore> candidates, UUID regionId) {
        try {
            int index = redisRoundRobinService.getNextIndex(regionId, candidates.size());
            return candidates.get(index).staffId();
        } catch (Exception ex) {

            int index = ThreadLocalRandom.current().nextInt(candidates.size());
            return candidates.get(index).staffId();
        }
    }
}

