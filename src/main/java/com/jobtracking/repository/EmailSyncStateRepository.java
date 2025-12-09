package com.jobtracking.repository;

import com.jobtracking.model.EmailSyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailSyncStateRepository extends JpaRepository<EmailSyncState, UUID> {
    
    // Find email sync state for a specific user
    // Note: Use User_Id (with underscore) to traverse the ManyToOne relationship
    Optional<EmailSyncState> findByUser_Id(UUID userId);
}