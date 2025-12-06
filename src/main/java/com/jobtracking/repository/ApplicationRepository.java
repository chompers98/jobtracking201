package com.jobtracking.repository;

import com.jobtracking.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {
    // Find by company name (case insensitive)
    List<Application> findByCompanyContainingIgnoreCase(String company);

    // Find all applications for a specific company
    List<Application> findAllByCompany(String company);

    // Find specific application by company + title combo (for duplicate detection)
    Application findByCompanyAndTitle(String company, String title);

    // Find by job link (optional - for future enhancement)
    Application findByJobLink(String jobLink);
}