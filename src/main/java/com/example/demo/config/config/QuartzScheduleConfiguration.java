package com.example.demo.config.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import com.example.demo.infra.quartz.factory.AutowiringSpringBeanJobFactory;

/**
 * Quartz 排程配置類
 */
@Configuration
public class QuartzScheduleConfiguration {

	@Bean
	public AutowiringSpringBeanJobFactory jobFactory(ApplicationContext applicationContext) {
		AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
		jobFactory.setApplicationContext(applicationContext);
		return jobFactory;
	}

	@Bean
	@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "spring.quartz")
	public QuartzCustomProperties quartzCustomProperties() {
		return new QuartzCustomProperties();
	}

	public static class QuartzCustomProperties {
		private Map<String, String> properties = new HashMap<>();

		public Map<String, String> getProperties() {
			return properties;
		}

		public void setProperties(java.util.Map<String, String> properties) {
			this.properties = properties;
		}
	}

	@Bean
	public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource, AutowiringSpringBeanJobFactory jobFactory,
			QuartzCustomProperties quartzCustomProperties) {
		SchedulerFactoryBean factory = new SchedulerFactoryBean();
		factory.setDataSource(dataSource);
		factory.setJobFactory(jobFactory);

		// 建立 Properties 物件，並直接載入 application.properties 中的所有 spring.quartz.properties.* 設定
		Properties properties = new Properties();
		if (quartzCustomProperties.getProperties() != null) {
			properties.putAll(quartzCustomProperties.getProperties());
		}
		
		// 設置 Quartz 屬性與自定義的 JobFactory
		factory.setQuartzProperties(properties);
		return factory;
	}

}