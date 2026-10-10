package com.msspoker.authservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class RegistrationInfrastructureConfiguration {
    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer("rabbitmq:4.1-management-alpine");
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27.0"))
                .withExposedPorts(1025, 8025)
                .waitingFor(Wait.forHttp("/readyz").forPort(8025));
    }

    @Bean
    JavaMailSender testMailSender(GenericContainer<?> mailpitContainer) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailpitContainer.getHost());
        sender.setPort(mailpitContainer.getMappedPort(1025));
        sender.setDefaultEncoding("UTF-8");
        sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "3000");
        sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "3000");
        sender.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "3000");
        return sender;
    }
}
