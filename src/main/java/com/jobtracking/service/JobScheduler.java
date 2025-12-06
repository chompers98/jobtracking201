package com.jobtracking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class JobScheduler {

    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);
    private final JobRecommendationService recommendationService;

    public JobScheduler(JobRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    // Run every day at 8:00 AM
    @Scheduled(cron = "0 0 8 * * *")
    public void fetchJobsDaily() {
        logger.info("Starting daily job fetch...");
        performFetch();
    }

    // Also run on startup if the database is empty, so the app isn't empty on first run
    @PostConstruct
    public void init() {
        if (recommendationService.getAllJobs().isEmpty()) {
            logger.info("Database is empty on startup. Performing initial fetch...");
            performFetch();
        }
    }

    private void performFetch() {
        try {
            // Default search parameters
            String query = "software engineer";
            String location = "United States";

            int count = recommendationService.refreshJobs(query, location);
            logger.info("Job fetch completed. Updated/Saved {} jobs.", count);
        } catch (Exception e) {
            logger.error("Error during job fetch: {}", e.getMessage());
        }
    }
}
