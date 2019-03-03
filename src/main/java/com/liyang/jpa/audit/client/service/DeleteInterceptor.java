package com.liyang.jpa.audit.client.service;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.MapDifference.ValueDifference;
import com.liyang.jpa.audit.client.common.CommonUtils;
import com.liyang.jpa.audit.server.common.LinkType;
import com.liyang.jpa.audit.server.common.Operate;
import com.liyang.jpa.audit.server.domain.AuditLog;
import com.liyang.jpa.audit.server.domain.DiffItem;
import com.liyang.jpa.audit.server.domain.DiffItem.Type;
import com.liyang.jpa.mysql.config.JpaSmartQuerySupport;
import com.liyang.jpa.mysql.db.SmartQuery;
import com.liyang.jpa.restful.core.interceptor.JpaRestfulDeleteInterceptor;
import com.liyang.jpa.restful.core.response.HTTPPostOkResponse;

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
	public String path() {
		return "/**";
	}

	@Override
	public int order() {
		return 100000;
	}

	@Override
	public boolean preHandle(String requestPath, Object oldInstance, Map<Object, Object> context) {
		AuditLog auditLog = new AuditLog();
		context.put("auditLog", auditLog);
		PathMatcher matcher = new AntPathMatcher();

		auditLog.setApplication(application);
		auditLog.setRequestPath(requestPath);
		auditLog.setTerminal(CommonUtils.getTerminal());
		auditLog.setIp(CommonUtils.getIP());
		auditLog.setCreateAt(new Date());
		String[] split = requestPath.split("/");

		if (matcher.match("/*/*", requestPath)) {
			auditLog.setOwnerResource(split[1]);
			auditLog.setOwnerUuid(split[2]);
			auditLog.setLinkType(LinkType.DIRECT);
			auditLog.setOperate(Operate.DELETE);

			Set<String> keySet = JpaSmartQuerySupport.getStructure(split[1]).getObjectFields().keySet();
			Map fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(),
					"uuid=" + auditLog.getOwnerUuid() + "&fields=*," + String.join(",", keySet));

			Set<String> keySet2 = JpaSmartQuerySupport.getStructure(split[1]).getSimpleFields().keySet();
			HashSet<String> hashSet = new HashSet<String>();
			hashSet.addAll(keySet);
			hashSet.addAll(keySet2);
			context.put("affectedFields", hashSet);
			context.put("oldMap", fetchOne);

		} else if (matcher.match("/*/*/*/*", requestPath)) {
			// 桥接删除
			auditLog.setLinkType(LinkType.BRIDGE);
			auditLog.setOperate(Operate.LINK_DELETE);
			auditLog.setOwnerResource(split[1]);
			auditLog.setOwnerUuid(split[2]);

			Map fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(),
					"uuid=" + auditLog.getOwnerUuid()+"&"+split[3]+".uuid="+split[4] + "&fields="+split[3]);
			HashSet<String> hashSet = new HashSet<String>();
			hashSet.add(split[3]);
			context.put("affectedFields", hashSet);
			context.put("oldMap", fetchOne);
		}
		return true;
	}

	@Override
	public HTTPPostOkResponse postHandle(String requestPath, HTTPPostOkResponse httpPostOkResponse,
			Map<Object, Object> context) {
		AuditLog auditLog = (AuditLog) context.get("auditLog");
		auditLog.setUuid(httpPostOkResponse.getUuid());

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
		ObjectMapper mapper = new ObjectMapper();
		String writeValueAsString = null;
		try {
			writeValueAsString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(details);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		auditLog.setDifference(writeValueAsString);
		auditService.add(auditLog);
		return httpPostOkResponse;
	}

}