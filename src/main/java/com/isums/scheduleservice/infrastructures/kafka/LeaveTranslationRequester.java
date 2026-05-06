package com.isums.scheduleservice.infrastructures.kafka;

import com.isums.common.i18n.SupportedLocales;
import com.isums.common.i18n.TranslationMap;
import com.isums.common.i18n.events.TextTranslationRequestedEvent;
import com.isums.common.i18n.events.TranslationIntent;
import com.isums.scheduleservice.domains.entities.LeaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeaveTranslationRequester {

    static final String CALLBACK_TOPIC = "text.translation.result.schedule";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${isums.i18n.schedule.auto-translate:true}")
    private boolean autoTranslate;

    @Value("${isums.i18n.schedule.required-locales:vi,en,ja}")
    private String requiredLocalesCsv;

    @Value("${isums.i18n.schedule.default-source:vi}")
    private String defaultSourceLanguage;

    public void requestForLeave(LeaveRequest leave) {
        if (!autoTranslate || leave == null || leave.getId() == null) return;
        if (leave.getNote() != null && !leave.getNote().isBlank()) {
            publishIfMissing(leave.getId(), "leave-request.note", "note",
                    leave.getNote(), leave.getNoteTranslations());
        }
        if (leave.getDecisionNote() != null && !leave.getDecisionNote().isBlank()) {
            publishIfMissing(leave.getId(), "leave-request.decisionNote", "decisionNote",
                    leave.getDecisionNote(), leave.getDecisionNoteTranslations());
        }
    }

    private void publishIfMissing(UUID resourceId, String resourceType, String fieldName,
                                  String text, TranslationMap existing) {
        Set<String> required = parseLocales();
        Set<String> have = existing == null ? Set.of() : existing.languagesPresent();
        List<String> missing = new ArrayList<>();
        for (String locale : required) {
            if (locale.equals(defaultSourceLanguage)) continue;
            if (!have.contains(locale)) missing.add(locale);
        }
        if (missing.isEmpty()) return;

        TextTranslationRequestedEvent event = new TextTranslationRequestedEvent(
                UUID.randomUUID(),
                resourceType,
                resourceId,
                fieldName,
                text,
                defaultSourceLanguage,
                missing,
                TranslationIntent.STAFF_INTERNAL,
                Boolean.FALSE,
                Instant.now(),
                CALLBACK_TOPIC);
        try {
            kafkaTemplate.send(TextTranslationRequestedEvent.TOPIC, resourceId.toString(), event);
        } catch (Exception ex) {
            log.warn("Failed to publish leave translation request resourceId={}: {}",
                    resourceId, ex.toString());
        }
    }

    private Set<String> parseLocales() {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : requiredLocalesCsv.split(",")) {
            String code = TranslationMap.normalizeLanguage(raw);
            if (code != null && SupportedLocales.isSupported(code)) out.add(code);
        }
        if (out.isEmpty()) out.addAll(SupportedLocales.ALL);
        return out;
    }
}
