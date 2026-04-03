package com.isums.scheduleservice.infrastructures.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.domains.enums.JobAction;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
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

    @KafkaListener(topics = "job.created", groupId = "schedule-group")
    public void handleCreated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JobEvent event = objectMapper.readValue(record.value(), JobEvent.class);

            if (event.getAction() != JobAction.JOB_CREATED) {
                ack.acknowledge();
                return;
            }

            workSlotService.handleAutoAssign(event);

            ack.acknowledge();

            log.info("[Schedule] JOB_CREATED handled jobId={}", event.getReferenceId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Schedule] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Schedule] handleCreated failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
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