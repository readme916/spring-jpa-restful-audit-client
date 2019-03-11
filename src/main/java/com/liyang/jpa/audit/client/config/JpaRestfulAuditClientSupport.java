package com.liyang.jpa.audit.client.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;


@ComponentScan("com.liyang.jpa.audit.client.service")
@EnableFeignClients(basePackages="com.liyang.jpa.audit.client.service")
@ConditionalOnProperty("spring.jpa.restful.audit.server-name")
public class JpaRestfulAuditClientSupport {

}
