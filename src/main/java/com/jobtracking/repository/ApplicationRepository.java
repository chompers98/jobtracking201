package com.jobtracking.repository;

import com.jobtracking.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {
    // Custom query to find by company name (case insensitive for better matching)
    List<Application> findByCompanyContainingIgnoreCase(String company);
}

