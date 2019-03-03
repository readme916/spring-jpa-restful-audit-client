package com.liyang.jpa.audit.client.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;


@ComponentScan("com.liyang.jpa.audit.client.service")
@EnableFeignClients(basePackages="com.liyang.jpa.audit.client.service")
public class JpaRestfulAuditClientSupport {

}
