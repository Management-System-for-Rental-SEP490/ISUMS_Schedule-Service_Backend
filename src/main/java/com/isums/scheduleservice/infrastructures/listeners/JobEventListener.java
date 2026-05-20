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
    public void handleCreated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.error("[Schedule] >>> ENTRY topic={} part={} offset={} keyNull={} valNull={} valLen={}",
                record.topic(), record.partition(), record.offset(),
                record.key() == null, record.value() == null,
                record.value() != null ? record.value().length() : -1);
        try {
            if (record.value() == null) {
                log.error("[Schedule] Null value at offset={}, ack and skip", record.offset());
                ack.acknowledge();
                return;
            }
            JobEvent event = objectMapper.readValue(record.value(), JobEvent.class);
            log.info("[Schedule] Parsed event refId={} refType={} action={}",
                    event.getReferenceId(), event.getReferenceType(), event.getAction());

            if (event.getAction() != JobAction.JOB_CREATED) {
                ack.acknowledge();
                return;
            }

            AutoAssignStrategy strategy = factory.getStrategy(event.getReferenceType());
            strategy.handle(event);

            ack.acknowledge();

            log.info("[Schedule] JOB_CREATED handled jobId={}", event.getReferenceId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Schedule] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
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