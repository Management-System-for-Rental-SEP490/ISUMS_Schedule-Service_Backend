package com.isums.scheduleservice.services;

import com.isums.scheduleservice.domains.dtos.CreateScheduleTemplateRequest;
import com.isums.scheduleservice.domains.entities.ScheduleTemplate;
import com.isums.scheduleservice.infrastructures.mapper.ScheduleMapper;
import com.isums.scheduleservice.infrastructures.repositories.ScheduleTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleTemplateServiceImpl")
class ScheduleTemplateServiceImplTest {

    @Mock private ScheduleTemplateRepository templateRepo;
    @Mock private ScheduleMapper mapper;

    @InjectMocks private ScheduleTemplateServiceImpl service;

    @Test
    @DisplayName("createTemplate builds entity from request and saves")
    void create() {
        CreateScheduleTemplateRequest req = new CreateScheduleTemplateRequest(
                "MON,TUE,WED", LocalTime.of(8, 0), LocalTime.of(12, 0),
                LocalTime.of(13, 0), LocalTime.of(17, 0), 60, 15, LocalDate.now());

        service.createTemplate(req);

        ArgumentCaptor<ScheduleTemplate> cap = ArgumentCaptor.forClass(ScheduleTemplate.class);
        verify(templateRepo).save(cap.capture());
        ScheduleTemplate saved = cap.getValue();
        assertThat(saved.getWorkingDays()).isEqualTo("MON,TUE,WED");
        assertThat(saved.getOpenTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(saved.getSlotMinutes()).isEqualTo(60);
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("getAllTemplate delegates to findAll")
    void getAll() {
        when(templateRepo.findAll()).thenReturn(List.of());

        service.getAllTemplate();

        verify(templateRepo).findAll();
    }

    @Test
    @DisplayName("getCurrentTemplate returns template effective for given date")
    void getCurrent() {
        LocalDate date = LocalDate.now();
        ScheduleTemplate tem = ScheduleTemplate.builder()
                .workingDays("MON,TUE,WED").openTime(LocalTime.of(8, 0))
                .breakStart(LocalTime.of(12, 0)).breakEnd(LocalTime.of(13, 0))
                .closeTime(LocalTime.of(17, 0)).slotMinutes(60).bufferMinutes(15)
                .effectiveFrom(date).build();
        when(templateRepo.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date))
                .thenReturn(Optional.of(tem));

        service.getCurrentTemplate(date);

        verify(templateRepo).findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date);
    }

    @Test
    @DisplayName("getCurrentTemplate wraps when no template found")
    void getCurrentMissing() {
        LocalDate date = LocalDate.now();
        when(templateRepo.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentTemplate(date))
                .isInstanceOf(RuntimeException.class);
    }
}
