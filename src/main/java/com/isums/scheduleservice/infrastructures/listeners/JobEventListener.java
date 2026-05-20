package com.isums.scheduleservice.infrastructures.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.domains.enums.JobAction;
import com.isums.scheduleservice.infrastructures.abstracts.AutoAssignStrategy;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import com.isums.scheduleservice.services.AutoAssignStrategy.AutoAssignStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobEventListener {

    private final WorkSlotService workSlotService;
    private final ObjectMapper objectMapper;
    private final AutoAssignStrategyFactory factory;

    @KafkaListener(topics = "job.created", groupId = "schedule-group")
    public void handleCreated(String payload, Acknowledgment ack) {
        log.error("[Schedule] >>> ENTRY len={}", payload == null ? -1 : payload.length());
        try {
            if (payload == null || payload.isBlank()) {
                log.error("[Schedule] Null/empty payload, ack and skip");
                ack.acknowledge();
                return;
            }
            JobEvent event = objectMapper.readValue(payload, JobEvent.class);
            log.info("[Schedule] Parsed refId={} refType={} action={}",
                    event.getReferenceId(), event.getReferenceType(), event.getAction());

            if (event.getAction() != JobAction.JOB_CREATED) {
                ack.acknowledge();
                return;
            }

            AutoAssignStrategy strategy = factory.getStrategy(event.getReferenceType());
            strategy.handle(event);

            ack.acknowledge();
            log.info("[Schedule] JOB_CREATED handled jobId={}", event.getReferenceId());

        } catch (Exception e) {
            log.error("[Schedule] handleCreated failed: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "job.completed", groupId = "schedule-group")
    public void handleCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JobEvent event = objectMapper.readValue(record.value(), JobEvent.class);

            if (event.getAction() != JobAction.JOB_COMPLETED) {
                ack.acknowledge();
                return;
            }

            workSlotService.markSlotDone(event);

            ack.acknowledge();

            log.info("[Schedule] JOB_COMPLETED handled jobId={} slotId={}",
                    event.getReferenceId(),
                    event.getSlotId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Schedule] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Schedule] handleCompleted failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}