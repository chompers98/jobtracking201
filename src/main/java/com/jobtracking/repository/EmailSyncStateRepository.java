package com.jobtracking.repository;

import com.jobtracking.model.EmailSyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailSyncStateRepository extends JpaRepository<EmailSyncState, Long> {
}