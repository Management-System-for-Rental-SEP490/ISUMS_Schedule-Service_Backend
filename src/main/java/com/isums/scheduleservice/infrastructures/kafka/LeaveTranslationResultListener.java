package com.isums.scheduleservice.infrastructures.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.common.i18n.TranslationMap;
import com.isums.common.i18n.events.TextTranslationResultEvent;
import com.isums.scheduleservice.domains.entities.LeaveRequest;
import com.isums.scheduleservice.infrastructures.repositories.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeaveTranslationResultListener {

    private final ObjectMapper objectMapper;
    private final LeaveRequestRepository repository;

    @KafkaListener(topics = LeaveTranslationRequester.CALLBACK_TOPIC,
            groupId = "schedule-translation-result")
    @Transactional
    public void onResult(String payload, Acknowledgment ack) {
        try {
            TextTranslationResultEvent ev = objectMapper.readValue(payload, TextTranslationResultEvent.class);
            if (!TextTranslationResultEvent.STATUS_DONE.equals(ev.status())
                    || ev.translatedText() == null || ev.translatedText().isBlank()) {
                ack.acknowledge();
                return;
            }
            apply(ev);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to apply leave translation result", e);
            ack.acknowledge();
        }
    }

    private void apply(TextTranslationResultEvent ev) {
        Optional<LeaveRequest> opt = repository.findById(ev.resourceId());
        if (opt.isEmpty()) return;
        LeaveRequest leave = opt.get();
        Map<String, String> patch = new LinkedHashMap<>();
        patch.put(ev.targetLanguage(), ev.translatedText());

        switch (ev.resourceType()) {
            case "leave-request.note" -> {
                TranslationMap before = leave.getNoteTranslations() == null ? TranslationMap.empty() : leave.getNoteTranslations();
                leave.setNoteTranslations(before.mergeAutoFilled(patch));
            }
            case "leave-request.decisionNote" -> {
                TranslationMap before = leave.getDecisionNoteTranslations() == null ? TranslationMap.empty() : leave.getDecisionNoteTranslations();
                leave.setDecisionNoteTranslations(before.mergeAutoFilled(patch));
            }
            default -> {
                log.warn("Unknown resourceType for leave translation: {}", ev.resourceType());
                return;
            }
        }
        repository.save(leave);
    }
}
