package com.example.demo.application.shared.command;

public record CreateJobCommand(String name, String group, String cronExpression, String jobType) {
}
