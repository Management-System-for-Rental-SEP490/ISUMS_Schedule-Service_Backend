package com.isums.scheduleservice.services;

import com.isums.scheduleservice.domains.dtos.CreateScheduleTemplateRequest;
import com.isums.scheduleservice.domains.dtos.ScheduleTemplateDto;
import com.isums.scheduleservice.domains.entities.ScheduleTemplate;
import com.isums.scheduleservice.infrastructures.abstracts.ScheduleTemplateService;
import com.isums.scheduleservice.infrastructures.mapper.ScheduleMapper;
import com.isums.scheduleservice.infrastructures.repositories.ScheduleTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleTemplateServiceImpl implements ScheduleTemplateService {
    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final ScheduleMapper scheduleMapper;
    @Override
    public ScheduleTemplateDto createTemplate(CreateScheduleTemplateRequest req) {
        try{
            ScheduleTemplate tem = ScheduleTemplate.builder()
                    .workingDays(req.workingDays())
                    .openTime(req.openTime())
                    .breakStart(req.breakStart())
                    .breakEnd(req.breakEnd())
                    .closeTime(req.closeTime())
                    .slotMinutes(req.slotMinutes())
                    .bufferMinutes(req.bufferMinutes())
                    .effectiveFrom((req.effectiveFrom()))
                    .updatedAt(Instant.now())
                    .build();

            scheduleTemplateRepository.save(tem);

            return scheduleMapper.schedule(tem);

        } catch (Exception ex) {
            throw new RuntimeException("Can't create template" + ex.getMessage());
        }
    }

    @Override
    public List<ScheduleTemplateDto> getAllTemplate() {
        try{
            List<ScheduleTemplate> templates = scheduleTemplateRepository.findAll();
            return  scheduleMapper.schedules(templates);

        }catch (Exception ex) {
            throw new RuntimeException("Can't get all template" + ex.getMessage());
        }
    }

    @Override
    public ScheduleTemplateDto getCurrentTemplate(LocalDate date) {
        try{
            ScheduleTemplate template = scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date)
                    .orElseThrow(() -> new RuntimeException("schedule not found"));

            return scheduleMapper.schedule(template);

        } catch (Exception ex) {
            throw new RuntimeException("Can't get current template" + ex.getMessage());
        }
    }
}
