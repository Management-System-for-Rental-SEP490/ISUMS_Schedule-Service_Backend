//package com.isums.scheduleservice.infrastructures.listeners;
//
//
//import com.isums.scheduleservice.domains.entities.WorkSlot;
//import com.isums.scheduleservice.domains.events.SlotEvent;
//import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Component;
//
//@Component
//@RequiredArgsConstructor
//public class SlotEventListeners {
//    private final WorkSlotService workSlotService;
//
//    @KafkaListener(topics = "slot.status.topic",groupId = "schedule-group")
//    public void handle(SlotEvent event){
//        workSlotService.markSlotDone(event);
//    }
//}
