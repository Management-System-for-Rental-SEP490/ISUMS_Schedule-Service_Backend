package com.isums.scheduleservice.infrastructures.listeners;


import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.JobAction;
import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobEventListener {
    private final WorkSlotService workSlotService;

    @KafkaListener(topics = "job.created", groupId = "schedule-group")
    public void handle(JobEvent event){
        if(event.getAction() == JobAction.JOB_CREATED){
            workSlotService.handleAutoAssign(event);
        }
    }

    @KafkaListener(topics = "job.completed", groupId = "schedule-group")
    public void handleCompleted(JobEvent event) {

        if(event.getAction() == JobAction.JOB_COMPLETED){
            workSlotService.markSlotDone(event);
        }


    }
}
