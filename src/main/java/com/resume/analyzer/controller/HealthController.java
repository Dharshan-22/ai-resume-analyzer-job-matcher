package com.resume.analyzer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for health and database connectivity verification.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @Autowired(required = false)
    private DataSource dataSource;

    /**
     * General health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "AI Resume Analyzer Backend is running smoothly!");
        response.put("version", "1.0.0");
        response.put("timestamp", LocalDateTime.now().toString());

        // Check DB status non-blockingly
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                response.put("database", conn.isValid(2) ? "CONNECTED" : "UNREACHABLE");
            } catch (Exception e) {
                response.put("database", "DISCONNECTED: " + e.getMessage());
            }
        } else {
            response.put("database", "NOT_CONFIGURED");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Dedicated database verification endpoint.
     * Executes an actual SQL query against MySQL to verify credentials, database selection, and version.
     */
    @GetMapping("/db-check")
    public ResponseEntity<Map<String, Object>> verifyDatabaseConnection() {
        Map<String, Object> response = new HashMap<>();

        if (dataSource == null) {
            response.put("status", "ERROR");
            response.put("message", "DataSource bean is not initialized. Please verify your Spring Data JPA configuration.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DATABASE() AS current_db, VERSION() AS server_ver, 1 AS ping_test")) {

            if (rs.next()) {
                String currentDb = rs.getString("current_db");
                String serverVer = rs.getString("server_ver");
                int pingVal = rs.getInt("ping_test");

                response.put("status", "SUCCESS");
                response.put("database", currentDb);
                response.put("mysqlVersion", serverVer);
                response.put("queryVerification", "Ping test query returned: " + pingVal);
                response.put("message", "Spring Boot is successfully connected to MySQL database: " + currentDb);
                response.put("timestamp", LocalDateTime.now().toString());

                return ResponseEntity.ok(response);
            } else {
                response.put("status", "WARNING");
                response.put("message", "Query executed but returned no rows.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

        } catch (Exception ex) {
            response.put("status", "FAILED");
            response.put("error", ex.getClass().getSimpleName());
            response.put("errorMessage", ex.getMessage());
            response.put("recommendation", "Please check your MySQL password in src/main/resources/application-local.properties.");
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
