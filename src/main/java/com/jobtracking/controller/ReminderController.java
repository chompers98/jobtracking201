package com.jobtracking.controller;

import com.jobtracking.model.Reminder;
import com.jobtracking.repository.ReminderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reminders")
@CrossOrigin(origins = "*")
public class ReminderController {

    private final ReminderRepository reminderRepository;

    public ReminderController(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    @GetMapping
    public List<Reminder> getAllReminders() {
        return reminderRepository.findAll();
    }

    @PostMapping
    public Reminder createReminder(@RequestBody Reminder reminder) {
        if (reminder.getCreatedAt() == null) {
            reminder.setCreatedAt(LocalDate.now());
        }
        return reminderRepository.save(reminder);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reminder> getReminder(@PathVariable Long id) {
        return reminderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Reminder> updateReminder(@PathVariable Long id, @RequestBody Reminder details) {
        return reminderRepository.findById(id)
                .map(reminder -> {
                    reminder.setTitle(details.getTitle());
                    reminder.setNotes(details.getNotes());
                    reminder.setColor(details.getColor());
                    reminder.setTriggerAt(details.getTriggerAt());
                    
                    if ("INTERVIEW".equals(reminder.getKind())) {
                         reminder.setEndDate(details.getEndDate());
                         reminder.setStartTime(details.getStartTime());
                         reminder.setEndTime(details.getEndTime());
                         reminder.setLocation(details.getLocation());
                         reminder.setMeetingLink(details.getMeetingLink());
                    }
                    
                    return ResponseEntity.ok(reminderRepository.save(reminder));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReminder(@PathVariable Long id) {
        return reminderRepository.findById(id)
                .map(reminder -> {
                    reminderRepository.delete(reminder);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

