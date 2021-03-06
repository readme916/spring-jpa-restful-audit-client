package com.liyang.jpa.audit.client.service;

import java.security.Principal;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import com.liyang.jpa.audit.client.common.CommonUtils;
import com.liyang.jpa.audit.server.common.LinkType;
import com.liyang.jpa.audit.server.domain.AuditLog;
import com.liyang.jpa.audit.server.domain.DiffItem;
import com.liyang.jpa.audit.server.domain.DiffItem.Type;
import com.liyang.jpa.restful.core.interceptor.JpaRestfulDeleteInterceptor;
import com.liyang.jpa.restful.core.response.HTTPPostOkResponse;
import com.liyang.jpa.smart.query.db.SmartQuery;

@Service
public class DeleteInterceptor implements JpaRestfulDeleteInterceptor {
	
	@Value(value = "${spring.application.name}")
	private String application;

	@Autowired
	private AuditService auditService;

	@Override
	public String name() {
		return "审计系统拦截器";
	}

	@Override
	public String description() {
		return "审计系统会把修改历史，传到专用记录服务器";
	}

	@Override
	public String[] path() {
		return new String[] { "/**" };
	}

	@Override
	public int order() {
		return 100000;
	}

	@Override
	public boolean preHandle(String requestPath, Object oldInstance, Map<String, Object> context) {
		AuditLog auditLog = new AuditLog();
		context.put("auditLog", auditLog);
		PathMatcher matcher = new AntPathMatcher();

		Principal principal = getPrincipal();
		if (principal == null) {
			auditLog.setCreatedBy("anonymousUser");
		} else {
			Map<String, Object> objectToMap = com.liyang.jpa.restful.core.utils.CommonUtils.objectToMap(principal);
			Map oauth2Request = (Map) objectToMap.get("oauth2Request");
			if (oauth2Request != null) {
				Object clientId = oauth2Request.get("clientId");
				auditLog.setClient(clientId.toString());
			}
			auditLog.setCreatedBy(principal.getName());
		}

		auditLog.setApplication(application);
		auditLog.setRequestPath(requestPath);
		auditLog.setIp(CommonUtils.getIPAddress());
		auditLog.setCreatedAt(new Date());
		String[] split = requestPath.split("/");

		if (matcher.match("/*/*", requestPath)) {
			auditLog.setResource(split[1]);
			auditLog.setUuid(split[2]);
			auditLog.setLinkType(LinkType.DIRECT);
			auditLog.setEvent("delete");

			Set<String> keySet = SmartQuery.getStructure(split[1]).getObjectFields().keySet();
			Map fetchOne = (Map) SmartQuery.fetchOne(auditLog.getResource(),
					"uuid=" + auditLog.getUuid() + "&fields=*," + String.join(",", keySet));

			Set<String> keySet2 = SmartQuery.getStructure(split[1]).getSimpleFields().keySet();
			HashSet<String> hashSet = new HashSet<String>();
			hashSet.addAll(keySet);
			hashSet.addAll(keySet2);
			context.put("affectedFields", hashSet);
			context.put("oldMap", fetchOne);

		} else if (matcher.match("/*/*/*/*", requestPath)) {
			// 桥接删除
			auditLog.setLinkType(LinkType.BRIDGE);
			auditLog.setEvent("unlink");
			auditLog.setResource(split[1]);
			auditLog.setUuid(split[2]);
			auditLog.setSubResource(split[3]);
			auditLog.setSubResourceId(split[4]);

			Map fetchOne = (Map) SmartQuery.fetchOne(auditLog.getResource(),
					"uuid=" + auditLog.getUuid() + "&" + split[3] + ".uuid=" + split[4] + "&fields=" + split[3]);
			HashSet<String> hashSet = new HashSet<String>();
			hashSet.add(split[3]);
			context.put("affectedFields", hashSet);
			context.put("oldMap", fetchOne);
		}
		return true;
	}

	@Override
	public HTTPPostOkResponse postHandle(String requestPath, HTTPPostOkResponse httpPostOkResponse,
			Map<String, Object> context) {
		AuditLog auditLog = (AuditLog) context.get("auditLog");
		HashMap<String, Object> hashMap = new HashMap<String, Object>();
		Set<String> keySet = (Set<String>) context.get("affectedFields");
		for (String str : keySet) {
			hashMap.put(str, null);
		}
		MapDifference<String, Object> differences = Maps.difference(hashMap, (Map) context.get("oldMap"));
		Map<String, ValueDifference<Object>> entriesDiffering = differences.entriesDiffering();

		HashMap<String, DiffItem> details = new HashMap<String, DiffItem>();
		Set<Entry<String, ValueDifference<Object>>> entrySet = entriesDiffering.entrySet();
		for (Entry<String, ValueDifference<Object>> entry : entrySet) {
			DiffItem diffItem = new DiffItem();
			String key = entry.getKey();
			ValueDifference<Object> difference = entry.getValue();
			Object rightValue = difference.rightValue();
			if (rightValue instanceof List) {
				diffItem.setType(Type.ARRAY);
			} else if (rightValue instanceof Map) {
				diffItem.setType(Type.OBJECT);
			} else {
				diffItem.setType(Type.SIMPLE);
			}
			diffItem.setNewValue(null);
			diffItem.setOldValue(difference.rightValue());
			details.put(key, diffItem);
		}
	
		auditLog.setDifference(details);
		auditService.add(auditLog);
		return httpPostOkResponse;
	}

	private Principal getPrincipal() {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
				.getRequest();
		if(request!=null) {
			return request.getUserPrincipal();
		}else {
			return null;
		}
	}
}
