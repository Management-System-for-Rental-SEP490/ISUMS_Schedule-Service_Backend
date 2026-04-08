package com.isums.scheduleservice.infrastructures.abstracts;

import com.isums.scheduleservice.domains.events.JobEvent;

public interface AutoAssignStrategy {
    boolean supports(String referenceType);
    void handle(JobEvent event);
}
