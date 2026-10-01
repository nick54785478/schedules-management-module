package com.example.demo.infra.quartz.factory;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * 讓 Quartz 產生的 Job 支援 Spring DI
 */
public class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

	/**
	 * AutowireCapableBeanFactory 是 Spring 的一個接口，允許我們對非 Spring 管理的物件執行依賴注入。
	 */
	private transient AutowireCapableBeanFactory beanFactory;

	@Override
	public void setApplicationContext(final ApplicationContext context) {
		this.beanFactory = context.getAutowireCapableBeanFactory();
	}

	/**
	 * 註冊排程 JOB 為 Spring 管理的實體
	 * 
	 * @param bundle 排程觸發的資訊
	 */
	@Override
	protected Object createJobInstance(final TriggerFiredBundle bundle) throws Exception {
		// 使用 createBean 可以同時支援 Constructor Injection 與 Field Injection
		return beanFactory.createBean(bundle.getJobDetail().getJobClass());
	}
}