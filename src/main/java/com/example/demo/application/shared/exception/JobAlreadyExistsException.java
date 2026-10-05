package com.example.demo.application.shared.exception;

public class JobAlreadyExistsException extends ScheduleModuleException {
    

    public JobAlreadyExistsException(String jobName, String jobGroup) {
        super(String.format("Job with name '%s' and group '%s' already exists.", jobName, jobGroup));
    }
}
