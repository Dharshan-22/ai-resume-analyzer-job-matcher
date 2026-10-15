package com.resume.analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the AI Resume Analyzer & Job Matcher application.
 * 
 * @SpringBootApplication enables:
 * 1. @Configuration - Tags the class as a source of bean definitions.
 * 2. @EnableAutoConfiguration - Tells Spring Boot to configure beans based on classpath settings.
 * 3. @ComponentScan - Tells Spring to scan and register beans in this package and sub-packages.
 */
@SpringBootApplication
public class AiResumeAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiResumeAnalyzerApplication.class, args);
        System.out.println("==================================================================");
        System.out.println(" AI Resume Analyzer & Job Matcher started successfully!");
        System.out.println(" Open your browser and navigate to: http://localhost:8080");
        System.out.println(" Health check endpoint: http://localhost:8080/api/health");
        System.out.println("==================================================================");
    }
}
