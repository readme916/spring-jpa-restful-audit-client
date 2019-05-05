package com.liyang.jpa.audit.client.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@ComponentScan("com.liyang.jpa.audit.client.service")
@EnableFeignClients(basePackages="com.liyang.jpa.audit.client.service")
@Configuration
public class JpaRestfulAuditClientSupport {

}
