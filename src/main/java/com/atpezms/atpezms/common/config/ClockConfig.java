package com.atpezms.atpezms.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides an injectable {@link Clock} so time-based logic is testable.
 */
@Configuration
public class ClockConfig {
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
