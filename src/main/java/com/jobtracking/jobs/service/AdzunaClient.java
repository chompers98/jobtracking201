package com.jobtracking.jobs.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jobtracking.jobs.model.Job;
import com.jobtracking.jobs.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

// Uses Adzuna Job Search API to fetch jobs and persist them.
// Requires ADZUNA_APP_ID and ADZUNA_APP_KEY environment variables.

@Service
public class AdzunaClient {

    private final RestTemplate restTemplate;
    private final JobRepository jobRepository;

    @Value("${adzuna.app-id}")
    private String appId;

    @Value("${adzuna.app-key}")
    private String appKey;

    @Value("${adzuna.country:us}")
    private String country;

    @Value("${adzuna.results-per-page:25}")
    private int resultsPerPage;

    public AdzunaClient(RestTemplate restTemplate, JobRepository jobRepository) {
        this.restTemplate = restTemplate;
        this.jobRepository = jobRepository;
    }

    public List<Job> fetchAndSaveJobs(String query, String location) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://api.adzuna.com/v1/api/jobs/" + country + "/search/1")
                .queryParam("app_id", appId)
                .queryParam("app_key", appKey)
                .queryParam("results_per_page", resultsPerPage)
                .queryParam("what", query)
                .queryParam("where", location)
                .build()
                .toUri();

        AdzunaResponse response =
                restTemplate.getForObject(uri, AdzunaResponse.class);

        List<Job> saved = new ArrayList<>();
        if (response != null && response.results != null) {
            for (AdzunaJob aj : response.results) {
                String salary = buildSalary(aj.salaryMin, aj.salaryMax);
                String company = aj.company != null ? aj.company.displayName : "";
                String loc = aj.location != null ? aj.location.displayName : "";

                Job job = new Job(
                        aj.title,
                        company,
                        salary,
                        aj.description,
                        loc,
                        aj.redirectUrl
                );
                saved.add(jobRepository.save(job));
            }
        }
        return saved;
    }

    private String buildSalary(Double min, Double max) {
        if (min == null && max == null) {
            return null;
        }
        if (min != null && max != null) {
            return String.format("%.0f - %.0f", min, max);
        }
        if (min != null) {
            return String.format("From %.0f", min);
        }
        return String.format("Up to %.0f", max);
    }

    // DTOs for parsing Adzuna JSON
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdzunaResponse {
        @JsonProperty("results")
        public List<AdzunaJob> results;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdzunaJob {
        @JsonProperty("title")
        public String title;

        @JsonProperty("description")
        public String description;

        @JsonProperty("redirect_url")
        public String redirectUrl;

        @JsonProperty("salary_min")
        public Double salaryMin;

        @JsonProperty("salary_max")
        public Double salaryMax;

        @JsonProperty("company")
        public AdzunaCompany company;

        @JsonProperty("location")
        public AdzunaLocation location;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdzunaCompany {
        @JsonProperty("display_name")
        public String displayName;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdzunaLocation {
        @JsonProperty("display_name")
        public String displayName;
    }
}
