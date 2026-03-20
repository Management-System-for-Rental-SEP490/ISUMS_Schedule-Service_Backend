package com.isums.scheduleservice.infrastructures.repositories;

import com.isums.scheduleservice.domains.entities.ScheduleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, UUID> {
    Optional<ScheduleTemplate>
    findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate date);
}
