package com.jobtracking.service;

import com.jobtracking.dto.JobDto;
import com.jobtracking.model.Job;
import com.jobtracking.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobRecommendationService {

    private final JobRepository jobRepository;
    private final AdzunaClient adzunaClient;

    public JobRecommendationService(JobRepository jobRepository,
                                    AdzunaClient adzunaClient) {
        this.jobRepository = jobRepository;
        this.adzunaClient = adzunaClient;
    }

    public int refreshJobs(String query, String location) {
        List<Job> saved = adzunaClient.fetchAndSaveJobs(query, location);
        return saved.size();
    }

    public List<JobDto> recommendJobs(List<String> skills, int limit) {
        if (skills == null || skills.isEmpty()) {
            return Collections.emptyList();
        }

        // normalize skills: lower-case and trim
        List<String> normalizedSkills = skills.stream()
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .map(String::trim)
                .collect(Collectors.toList());

        List<Job> allJobs = jobRepository.findAll();

        List<JobDto> scored = new ArrayList<>();
        for (Job job : allJobs) {
            String text = buildSearchText(job).toLowerCase();

            int score = 0;
            for (String skill : normalizedSkills) {
                if (skill.isEmpty()) {
                    continue;
                }
                if (text.contains(skill)) {
                    score++;
                }
            }

            if (score > 0) {
                scored.add(new JobDto(
                        job.getId(),
                        job.getTitle(),
                        job.getCompany(),
                        job.getSalary(),
                        job.getDescription(),
                        job.getLocation(),
                        job.getExternalUrl(),
                        score
                ));
            }
        }

        // sort by score descending, then title as tie-breaker
        scored.sort(Comparator
                .comparingInt(JobDto::getScore)
                .reversed()
                .thenComparing(JobDto::getTitle));

        if (limit > 0 && scored.size() > limit) {
            return scored.subList(0, limit);
        }
        return scored;
    }

    public List<Job> getAllJobs() {
        return jobRepository.findAll();
    }

    private String buildSearchText(Job job) {
        StringBuilder sb = new StringBuilder();
        if (job.getTitle() != null) {
            sb.append(job.getTitle()).append(" ");
        }
        if (job.getCompany() != null) {
            sb.append(job.getCompany()).append(" ");
        }
        if (job.getDescription() != null) {
            sb.append(job.getDescription());
        }
        return sb.toString();
    }
}
