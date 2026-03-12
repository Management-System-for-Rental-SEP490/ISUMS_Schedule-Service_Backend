package com.isums.scheduleservice.services;

import com.isums.scheduleservice.domains.dtos.*;
import com.isums.scheduleservice.domains.entities.ScheduleTemplate;
import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.infrastructures.abstracts.WorkSlotService;
import com.isums.scheduleservice.infrastructures.mapper.ScheduleMapper;
import com.isums.scheduleservice.infrastructures.repositories.ScheduleTemplateRepository;
import com.isums.scheduleservice.infrastructures.repositories.WorkSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkSlotServiceImpl implements WorkSlotService {
    private final WorkSlotRepository workSlotRepository;
    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final ScheduleMapper scheduleMapper;

    @Override
    public WorkSlotDto createSlots(CreateWorkSlotRequest req) {
        try{
            ScheduleTemplate template = scheduleTemplateRepository.findEffectiveTemplates(req.date())
                    .orElseThrow(() -> new RuntimeException("Template not found"));;

            List<WorkSlot> allSlots =new ArrayList<>();
            List<SlotPattern> patterns = generatePattern(template);

            for(int i = 0; i < req.days(); i++){

                LocalDate date = req.date().plusDays(i);

                // step 3: clone pattern for each staff
                for(UUID staffId : req.staffIds()){

                    allSlots.addAll(
                            createSlotsFromPattern(staffId, date, patterns)
                    );

                }
            }


            workSlotRepository.saveAll(allSlots);
            List<SlotTimeDto> slots = allSlots.stream()
                    .map(s -> new SlotTimeDto(
                            s.getStaffId(),
                            s.getStartTime(),
                            s.getEndTime(),
                            s.getStatus().name()
                    ))
                    .toList();
            return new WorkSlotDto(allSlots.size(),slots);

        } catch (Exception ex) {
            throw new RuntimeException("Can't create slot" + ex.getMessage());
        }
    }

    @Override
    public List<SlotDto> getAllWorkSlots() {
        try{
            List<WorkSlot> slots = workSlotRepository.findAllByOrderByStartTimeAsc();

            return scheduleMapper.slots(slots);

        }catch (Exception ex){
            throw new RuntimeException("Can't get all slots" + ex.getMessage());
        }
    }

    @Override
    public List<SlotDto> getWorkSlotsByStaff(UUID staffId) {
        try{
            List<WorkSlot> slots = workSlotRepository.findByStaffIdOrderByStartTimeAsc(staffId);

            return scheduleMapper.slots(slots);

        }catch (Exception ex){
            throw new RuntimeException("Can't get all slots" + ex.getMessage());
        }
    }

    private List<SlotPattern> generatePattern(ScheduleTemplate template){

        List<SlotPattern> patterns = new ArrayList<>();

        LocalTime cursor = template.getOpenTime();

        // morning
        while(!cursor.plusMinutes(template.getSlotMinutes())
                .isAfter(template.getBreakStart())){

            patterns.add(new SlotPattern(
                    cursor,
                    cursor.plusMinutes(template.getSlotMinutes())
            ));

            cursor = cursor.plusMinutes(
                    template.getSlotMinutes() + template.getBufferMinutes()
            );
        }

        cursor = template.getBreakEnd();

        // afternoon
        while(!cursor.plusMinutes(template.getSlotMinutes())
                .isAfter(template.getCloseTime())){

            patterns.add(new SlotPattern(
                    cursor,
                    cursor.plusMinutes(template.getSlotMinutes())
            ));

            cursor = cursor.plusMinutes(
                    template.getSlotMinutes() + template.getBufferMinutes()
            );
        }

        return patterns;
    }

    private List<WorkSlot> createSlotsFromPattern(

            UUID staffId,
            LocalDate date,
            List<SlotPattern> patterns

    ){

        List<WorkSlot> slots = new ArrayList<>();

        for(SlotPattern p : patterns){

            LocalDateTime start = LocalDateTime.of(date, p.start());

            WorkSlot slot = WorkSlot.builder()
                    .staffId(staffId)
                    .startTime(start)
                    .endTime(LocalDateTime.of(date, p.end()))
                    .status(SlotStatus.AVAILABLE)
                    .build();

            slots.add(slot);
        }

        return slots;
    }
}


