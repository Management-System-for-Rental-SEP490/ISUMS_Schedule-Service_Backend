package com.isums.scheduleservice.services.AutoAssignStrategy;

import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.infrastructures.abstracts.AutoAssignStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AutoAssignStrategyFactory")
class AutoAssignStrategyFactoryTest {

    private AutoAssignStrategy stub(String type) {
        return new AutoAssignStrategy() {
            @Override public boolean supports(String referenceType) {
                return type.equals(referenceType);
            }
            @Override public void handle(JobEvent event) { /* no-op */ }
        };
    }

    @Test
    @DisplayName("returns the first strategy that supports the given type")
    void returnsMatchingStrategy() {
        AutoAssignStrategy issue = stub("ISSUE");
        AutoAssignStrategy inspection = stub("INSPECTION");
        AutoAssignStrategyFactory factory = new AutoAssignStrategyFactory(List.of(issue, inspection));

        assertThat(factory.getStrategy("ISSUE")).isSameAs(issue);
        assertThat(factory.getStrategy("INSPECTION")).isSameAs(inspection);
    }

    @Test
    @DisplayName("throws when no strategy supports the given reference type")
    void throwsWhenNoMatch() {
        AutoAssignStrategyFactory factory = new AutoAssignStrategyFactory(List.of(stub("ISSUE")));

        assertThatThrownBy(() -> factory.getStrategy("UNKNOWN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No strategy found");
    }

    @Test
    @DisplayName("throws when strategy list is empty")
    void emptyListThrows() {
        AutoAssignStrategyFactory factory = new AutoAssignStrategyFactory(List.of());

        assertThatThrownBy(() -> factory.getStrategy("ISSUE"))
                .isInstanceOf(RuntimeException.class);
    }
}
