package com.jobtracking.repository;

import com.jobtracking.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    
    // ===== User-specific queries =====
    // Note: Use User_Id (with underscore) to traverse the ManyToOne relationship
    
    // Find all applications for a specific user
    List<Application> findByUser_Id(UUID userId);
    
    // Find by company name (case insensitive) for a specific user
    List<Application> findByUser_IdAndCompanyContainingIgnoreCase(UUID userId, String company);

    // Find all applications for a specific company and user
    List<Application> findAllByUser_IdAndCompany(UUID userId, String company);

    // Find specific application by user + company + title combo (for duplicate detection)
    Application findByUser_IdAndCompanyAndTitle(UUID userId, String company, String title);

    // Find by job link for a specific user
    Application findByUser_IdAndJobLink(UUID userId, String jobLink);
    
    // ===== Legacy queries (kept for backwards compatibility, but should be phased out) =====
    
    // Find by company name (case insensitive)
    List<Application> findByCompanyContainingIgnoreCase(String company);

    // Find all applications for a specific company
    List<Application> findAllByCompany(String company);

    // Find specific application by company + title combo (for duplicate detection)
    Application findByCompanyAndTitle(String company, String title);

    // Find by job link (optional - for future enhancement)
    Application findByJobLink(String jobLink);
}