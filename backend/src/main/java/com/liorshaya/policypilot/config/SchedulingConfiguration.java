package com.liorshaya.policypilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduled jobs run inside the API (Document 5, Authorization (protected demo): the nightly reset is a job, not an
 * endpoint): {@code demo.ResetJob} on {@code policypilot.demo.reset-cron}, which {@code -} turns off.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfiguration {}
