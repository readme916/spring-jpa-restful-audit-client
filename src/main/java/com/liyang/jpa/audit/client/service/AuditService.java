package com.liyang.jpa.audit.client.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.liyang.jpa.audit.server.domain.AuditLog;

@FeignClient("${spring.jpa.restful.audit.server-name:audit}")
public interface  AuditService {

	@RequestMapping(path="/api",method=RequestMethod.POST)
	public String add(@RequestBody AuditLog log);
}
