package com.isums.scheduleservice.infrastructures.kafka;

import com.isums.scheduleservice.domains.events.JobNeedRescheduleEvent;
import com.isums.scheduleservice.domains.events.JobRescheduledEvent;
import com.isums.scheduleservice.domains.events.JobScheduledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishJobScheduled(JobScheduledEvent event) {
        kafkaTemplate.send("job.scheduled", event.getReferenceId().toString(), event);
    }

    public void publishJobRescheduled(JobRescheduledEvent event){
        kafkaTemplate.send("job.rescheduled", event.getReferenceId().toString(),event);
    }

    public void publishJobNeedReschedule(JobNeedRescheduleEvent event) {
        kafkaTemplate.send("job.need-reschedule", event.getReferenceId().toString(), event);
    }

}
