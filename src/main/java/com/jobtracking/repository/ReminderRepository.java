package com.jobtracking.repository;

import com.jobtracking.model.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {
    List<Reminder> findByApplicationId(UUID applicationId);
    List<Reminder> findByApplicationIdIn(List<UUID> applicationIds);
    List<Reminder> findByUser_Id(UUID userId);
}

