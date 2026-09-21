package com.example.demo.application.domain.schedule.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterScheduleJobCommand {

	private String jobName;

	private String groupName;

	private String cronExpression;

	private String jobClass;
}