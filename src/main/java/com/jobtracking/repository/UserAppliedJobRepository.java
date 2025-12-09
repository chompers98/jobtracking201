package com.jobtracking.repository;

import com.jobtracking.model.UserAppliedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserAppliedJobRepository extends JpaRepository<UserAppliedJob, UUID> {
    
    // Check if a user has already applied to a specific job
    // Note: Use User_Id and Job_Id (with underscore) to traverse the ManyToOne relationships
    boolean existsByUser_IdAndJob_Id(UUID userId, UUID jobId);
    
    // Get all job IDs that a user has applied to
    @Query("SELECT uaj.job.id FROM UserAppliedJob uaj WHERE uaj.user.id = :userId")
    List<UUID> findJobIdsByUserId(@Param("userId") UUID userId);
    
    // Find all applied jobs for a user
    List<UserAppliedJob> findByUser_Id(UUID userId);
}
