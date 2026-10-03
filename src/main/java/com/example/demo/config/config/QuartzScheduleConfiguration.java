package com.example.demo.config.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import com.example.demo.infra.quartz.factory.AutowiringSpringBeanJobFactory;

/**
 * <h2>Quartz 排程核心組態設定</h2>
 * <p>
 * 負責初始化 Quartz 引擎的核心元件，包含 JobFactory 的依賴注入設定、
 * 從外部設定檔載入 Quartz 專屬參數，以及建立 Quartz Scheduler 實體。
 * </p>
 */
@Configuration
public class QuartzScheduleConfiguration {

	/**
	 * 設定自訂的 JobFactory。
	 * <p>
	 * 預設的 Quartz Job 是由 Quartz 自行實例化，無法享受 Spring 容器的依賴注入能力（如 @Autowired）。
	 * 此處註冊 {@link AutowiringSpringBeanJobFactory} 來橋接 Spring 與 Quartz，
	 * 使得排程任務內部也能自然使用 Spring Beans。
	 * </p>
	 *
	 * @param applicationContext Spring 的應用程式上下文
	 * @return 支援依賴注入的 JobFactory
	 */
	@Bean
	public AutowiringSpringBeanJobFactory jobFactory(ApplicationContext applicationContext) {
		AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
		jobFactory.setApplicationContext(applicationContext);
		return jobFactory;
	}

	/**
	 * 擷取並綁定前綴為 {@code spring.quartz} 的設定檔屬性。
	 *
	 * @return 包含自定義屬性的 QuartzCustomProperties 物件
	 */
	@Bean
	@ConfigurationProperties(prefix = "spring.quartz")
	public QuartzCustomProperties quartzCustomProperties() {
		return new QuartzCustomProperties();
	}

	/**
	 * 專門用來承載 {@code spring.quartz.properties.*} 屬性設定的配置類。
	 */
	public static class QuartzCustomProperties {
		/**
		 * 裝載所有以 {@code spring.quartz.properties} 結尾的鍵值對。
		 */
		private Map<String, String> properties = new HashMap<>();

		public Map<String, String> getProperties() {
			return properties;
		}

		public void setProperties(Map<String, String> properties) {
			this.properties = properties;
		}
	}

	/**
	 * 建立 Quartz 的核心排程器工廠 (SchedulerFactoryBean)。
	 * <p>
	 * 這裡將注入資料庫來源 (DataSource) 以支援叢集與持久化機制，
	 * 並套用支援 Spring DI 的 JobFactory，最後再掛載從設定檔解析的屬性參數。
	 * </p>
	 *
	 * @param dataSource             系統的共用資料來源
	 * @param jobFactory             具備依賴注入能力的 JobFactory
	 * @param quartzCustomProperties Quartz 自訂設定參數
	 * @return 配置完成的 SchedulerFactoryBean
	 */
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