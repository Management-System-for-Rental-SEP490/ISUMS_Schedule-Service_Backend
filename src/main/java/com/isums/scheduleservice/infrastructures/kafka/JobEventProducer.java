package com.isums.scheduleservice.infrastructures.kafka;

import com.isums.scheduleservice.domains.events.JobScheduledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishJobScheduled(JobScheduledEvent event) {
        kafkaTemplate.send("job.scheduled", event.getJobId().toString(), event);
    }
}
