package com.isums.scheduleservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.scheduleservice.domains.enums.JobAction;
import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.infrastructures.abstracts.AutoAssignStrategy;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import com.isums.scheduleservice.services.AutoAssignStrategy.AutoAssignStrategyFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobEventListener")
class JobEventListenerTest {

    @Mock private WorkSlotService workSlotService;
    @Mock private ObjectMapper objectMapper;
    @Mock private AutoAssignStrategyFactory factory;
    @Mock private AutoAssignStrategy strategy;
    @Mock private Acknowledgment ack;

    @InjectMocks private JobEventListener listener;

    private ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>("t", 0, 0L, "k", payload);
    }

    @Test
    @DisplayName("handleCreated routes to strategy when action is JOB_CREATED")
    void createdRoutes() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceId(UUID.randomUUID())
                .referenceType("ISSUE")
                .action(JobAction.JOB_CREATED).build();

        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);
        when(factory.getStrategy("ISSUE")).thenReturn(strategy);

        listener.handleCreated("{}");

        verify(strategy).handle(evt);
    }

    @Test
    @DisplayName("handleCreated skips strategy when action is not JOB_CREATED")
    void createdWrongAction() throws Exception {
        JobEvent evt = JobEvent.builder()
                .action(JobAction.JOB_ASSIGNED).build();

        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleCreated("{}");

        verify(factory, never()).getStrategy(any());
        verify(strategy, never()).handle(any());
    }

    @Test
    @DisplayName("handleCreated swallows poison-pill payload (malformed JSON) — does not call strategy")
    void createdPoisonPill() throws Exception {
        when(objectMapper.readValue(anyString(), eq(JobEvent.class)))
                .thenThrow(new JsonProcessingException("boom") {});

        listener.handleCreated("not-json");

        verify(factory, never()).getStrategy(any());
    }

    @Test
    @DisplayName("handleCreated swallows null payload (no work, no NPE)")
    void createdNullPayload() {
        listener.handleCreated(null);
        verifyNoInteractions(factory, strategy);
    }

    @Test
    @DisplayName("handleCreated swallows unexpected downstream errors (no retry — strategy failures must not pin the partition)")
    void createdUnexpectedFailure() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceType("ISSUE").action(JobAction.JOB_CREATED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);
        when(factory.getStrategy("ISSUE")).thenReturn(strategy);
        org.mockito.Mockito.doThrow(new RuntimeException("downstream")).when(strategy).handle(evt);

        listener.handleCreated("{}");

        verify(strategy).handle(evt);
    }

    @Test
    @DisplayName("handleCompleted delegates to workSlotService.markSlotDone and acks")
    void completedDelegates() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceId(UUID.randomUUID())
                .slotId(UUID.randomUUID())
                .action(JobAction.JOB_COMPLETED).build();

        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleCompleted(record("{}"), ack);

        verify(workSlotService).markSlotDone(evt);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("handleCompleted skips work when action is not JOB_COMPLETED")
    void completedWrongAction() throws Exception {
        JobEvent evt = JobEvent.builder().action(JobAction.JOB_CREATED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleCompleted(record("{}"), ack);

        verify(workSlotService, never()).markSlotDone(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("handleCompleted acks poison-pill payload without calling service")
    void completedPoisonPill() throws Exception {
        when(objectMapper.readValue(anyString(), eq(JobEvent.class)))
                .thenThrow(new JsonProcessingException("bad") {});

        listener.handleCompleted(record("garbage"), ack);

        verify(workSlotService, never()).markSlotDone(any());
        verify(ack).acknowledge();
    }
}
