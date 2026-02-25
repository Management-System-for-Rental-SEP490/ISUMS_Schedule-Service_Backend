package com.isums.scheduleservice.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {
    @GetMapping("/api/schedules/ping")
    public String ping() {
        return "schedule-service OK";
    }
}