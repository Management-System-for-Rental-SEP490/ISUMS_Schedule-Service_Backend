package com.isums.scheduleservice.controllers;

import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.domains.enums.JobAction;
import com.isums.scheduleservice.infrastructures.abstracts.AutoAssignStrategy;
import com.isums.scheduleservice.services.AutoAssignStrategy.AutoAssignStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/schedules/internal")
@RequiredArgsConstructor
@Slf4j
public class AutoAssignController {

    private final AutoAssignStrategyFactory factory;

    public record AutoAssignRequest(
            UUID referenceId,
            UUID tenantId,
            UUID houseId,
            String referenceType
    ) {}

    @PostMapping("/auto-assign")
    @PreAuthorize("permitAll()")
    public String autoAssign(@RequestBody AutoAssignRequest req) {
        log.info("[AutoAssign] REST referenceId={} type={} houseId={}",
                req.referenceId(), req.referenceType(), req.houseId());
        JobEvent event = JobEvent.builder()
                .referenceId(req.referenceId())
                .houseId(req.houseId())
                .referenceType(req.referenceType())
                .action(JobAction.JOB_CREATED)
                .build();
        AutoAssignStrategy strategy = factory.getStrategy(req.referenceType());
        strategy.handle(event);
        return "OK";
    }
}
