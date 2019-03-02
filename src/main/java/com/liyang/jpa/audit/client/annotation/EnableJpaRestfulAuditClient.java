package com.liyang.jpa.audit.client.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

import com.liyang.jpa.audit.client.config.JpaRestfulAuditClientSupport;

@Retention(RUNTIME)
@Target(TYPE)
@Inherited
@Import(JpaRestfulAuditClientSupport.class)
public @interface EnableJpaRestfulAuditClient {

}
