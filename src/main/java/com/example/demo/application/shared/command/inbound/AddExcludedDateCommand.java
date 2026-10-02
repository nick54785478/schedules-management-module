package com.example.demo.application.shared.command.inbound;

import java.time.LocalDate;

import java.util.UUID;

public record AddExcludedDateCommand(UUID calendarId, LocalDate excludedDate) {
}

