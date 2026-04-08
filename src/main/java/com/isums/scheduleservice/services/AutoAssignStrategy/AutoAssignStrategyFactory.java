package com.isums.scheduleservice.services.AutoAssignStrategy;

import com.isums.scheduleservice.infrastructures.abstracts.AutoAssignStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AutoAssignStrategyFactory {
    private final List<AutoAssignStrategy> strategies;

    public AutoAssignStrategy getStrategy(String referenceType) {
        return strategies.stream()
                .filter(s -> s.supports(referenceType))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No strategy found for type: " + referenceType));
    }
}
