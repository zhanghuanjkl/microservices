package com.github.sawied.microservice.trade.config;


import java.util.Properties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.mail.ImapMailReceiver;
import org.springframework.integration.mail.dsl.Mail;
import org.springframework.integration.mail.support.DefaultMailHeaderMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableIntegration
public class MailConfig {

	// @Bean
	public IntegrationFlow mailPop3Receiving(@Qualifier("emailMessageHandler") MessageHandler emailMessageHandler) {
		return IntegrationFlows
				.from(Mail.pop3InboundAdapter("pop3.mxhichina.com", 110, "ghost@sawied.top", "")
						.headerMapper(new DefaultMailHeaderMapper()).embeddedPartsAsBytes(true)
						.shouldDeleteMessages(true).get(), e -> e.poller(Pollers.fixedRate(5000)))
				.handle(emailMessageHandler).get();
	}

	@Bean
	public IntegrationFlow mailImapReceiving(ImapMailReceiver mailReceiver,
			@Qualifier("emailMessageHandler") MessageHandler emailMessageHandler) {

		Properties javaMailProperties = new Properties();
		javaMailProperties.put("mail.debug", "true");

		return IntegrationFlows.from(Mail.imapIdleAdapter(mailReceiver).autoStartup(true).get())
				.handle(emailMessageHandler).get();
	}

	@Bean
	public ImapMailReceiver mailReceiver() {
		ImapMailReceiver mailReceiver = new ImapMailReceiver(
				"imaps://danan.2009%40hotmail.com:0311711w@outlook.office365.com/INBOX");

		Properties javaMailProperties = new Properties();
		javaMailProperties.put("mail.debug", "true");
		mailReceiver.setJavaMailProperties(javaMailProperties);
		mailReceiver.setSimpleContent(false);
		mailReceiver.setShouldMarkMessagesAsRead(true);
		mailReceiver.setEmbeddedPartsAsBytes(false);
		mailReceiver.setMaxFetchSize(10);
		mailReceiver.setHeaderMapper(new MailContentHeaderMapper());
		return mailReceiver;
	}

	@Bean
	public TaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
		threadPoolTaskScheduler.setPoolSize(5);
		threadPoolTaskScheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
		return threadPoolTaskScheduler;
	}

	@Bean
	public MessageHandler emailMessageHandler() {
		return new MessageHandler() {

			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				 MessageHeaders headers = message.getHeaders();
				 System.out.println("headers");
			}

		};
	}

}
