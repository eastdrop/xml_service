package com.xmlservice.xmlservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataUpdateService {

    private final DatabaseService databaseService;

    @Scheduled(fixedDelayString = "${update.interval:3600000}") // Каждый час по умолчанию
    public void scheduledUpdate() {
        log.info("Starting scheduled database update");
        try {
            databaseService.update();
            log.info("Scheduled update completed successfully");
        } catch (Exception e) {
            log.error("Scheduled update failed", e);
        }
    }
}