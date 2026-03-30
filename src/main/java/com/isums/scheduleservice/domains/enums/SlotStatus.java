package com.isums.scheduleservice.domains.enums;

public enum SlotStatus {
    AVAILABLE,
    PENDING,
    WAITING_MANAGER_CONFIRM,
    CONFIRMED,
    BOOKED,
    BLOCKED,
    NEED_RESCHEDULE,
    CANCELLED,
    DONE
}