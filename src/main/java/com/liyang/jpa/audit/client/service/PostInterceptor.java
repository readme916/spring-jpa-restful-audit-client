package com.liyang.jpa.audit.client.service;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import com.liyang.jpa.audit.client.common.CommonUtils;
import com.liyang.jpa.audit.server.common.LinkType;
import com.liyang.jpa.audit.server.common.Operate;
import com.liyang.jpa.audit.server.domain.AuditLog;
import com.liyang.jpa.audit.server.domain.DiffItem;
import com.liyang.jpa.audit.server.domain.DiffItem.Type;
import com.liyang.jpa.mysql.config.JpaSmartQuerySupport;
import com.liyang.jpa.mysql.db.SmartQuery;
import com.liyang.jpa.mysql.db.structure.EntityStructure;
import com.liyang.jpa.restful.core.interceptor.JpaRestfulPostInterceptor;
import com.liyang.jpa.restful.core.response.HTTPPostOkResponse;


@Service
public class PostInterceptor implements JpaRestfulPostInterceptor {
	protected final static Logger logger = LoggerFactory.getLogger(PostInterceptor.class);

	@Value(value = "${spring.application.name}")
	private String application;
	
	@Autowired
    private AuditService auditService;

	@Override
	public String description() {
		// TODO Auto-generated method stub
		return "审计系统会把修改历史，传到记录服务器";
	}

	@Override
	public String name() {
		// TODO Auto-generated method stub
		return "审计post拦截器";
	}

	@Override
	public int order() {
		// TODO Auto-generated method stub
		return 100000;
	}

	@Override
	public String path() {
		// TODO Auto-generated method stub
		return "/**";
	}

	@Override

	public boolean preHandle(String requestPath, String requestBody, Object oldInstance, Map<Object, Object> context) {
		AuditLog auditLog = new AuditLog();
		context.put("auditLog", auditLog);
		PathMatcher matcher = new AntPathMatcher();

		auditLog.setApplication(application);
		auditLog.setPostBody(requestBody);
		auditLog.setRequestPath(requestPath);
		auditLog.setTerminal(CommonUtils.getTerminal());
		auditLog.setIp(CommonUtils.getIP());
		auditLog.setCreateAt(new Date());
		String[] split = requestPath.split("/");

		if (matcher.match("/*", requestPath)) {
			// 创建
			Set<String> affectedFields = FileterNullAndTransient(split[1], requestBody);
			auditLog.setOwnerResource(split[1]);
			auditLog.setLinkType(LinkType.DIRECT);
			auditLog.setOperate(Operate.CREATE);
			HashMap<String, Object> oldMap = new HashMap<String, Object>();
			for (String key : affectedFields) {
				oldMap.put(key, null);
			}
			context.put("affectedFields", String.join(",", affectedFields));
			context.put("oldMap", oldMap);

		} else if (matcher.match("/*/*", requestPath)) {
			// 更新
			Set<String> affectedFields = FileterNullAndTransient(split[1], requestBody);
			auditLog.setOwnerResource(split[1]);
			auditLog.setOwnerUuid(split[2]);
			auditLog.setLinkType(LinkType.DIRECT);
			auditLog.setOperate(Operate.UPDATE);
			Map fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(),
					"uuid=" + auditLog.getOwnerUuid() + "&fields=" + String.join(",", affectedFields));
			context.put("affectedFields", String.join(",", affectedFields));
			context.put("oldMap", fetchOne);

		} else if (matcher.match("/*/*/*", requestPath)) {
			// 桥接创建
			auditLog.setLinkType(LinkType.BRIDGE);
			auditLog.setOperate(Operate.LINK_CREATE);
			String subResourceName = com.liyang.jpa.restful.core.utils.CommonUtils.subResourceName(split[1], split[3]);
			
			Set<String> fields = FileterNullAndTransient(subResourceName, requestBody);
			auditLog.setOwnerResource(split[1]);
			auditLog.setOwnerUuid(split[2]);
			
			HashSet<String> affectedFields = new HashSet<String>();
			for (String str : fields) {
				affectedFields.add(split[3]+"."+str);
			}
			
			Map fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(),
					"uuid=" + auditLog.getOwnerUuid() + "&fields=" + String.join(",", affectedFields));
			
			context.put("affectedFields", String.join(",", affectedFields));
			context.put("oldMap", fetchOne);
			
		} else if (matcher.match("/*/*/*/*", requestPath)) {
			// 桥接更新
			auditLog.setLinkType(LinkType.BRIDGE);
			auditLog.setOperate(Operate.LINK_UPDATE);
			String subResourceName = com.liyang.jpa.restful.core.utils.CommonUtils.subResourceName(split[1], split[3]);
			Set<String> affectedFields = FileterNullAndTransient(subResourceName, requestBody);
			auditLog.setOwnerResource(subResourceName);
			auditLog.setOwnerUuid(split[4]);
			Map fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(),
					"uuid=" + auditLog.getOwnerUuid() + "&fields=" + String.join(",", affectedFields));
			
			context.put("affectedFields", String.join(",", affectedFields));
			context.put("oldMap", fetchOne);
			
		} else if (matcher.match("/*/*/*/*/*", requestPath)) {
			// 桥接桥接创建
			auditLog.setLinkType(LinkType.BRIDGE);
			auditLog.setOperate(Operate.LINK_CREATE);
			
			String subResourceName = com.liyang.jpa.restful.core.utils.CommonUtils.subResourceName(split[1], split[3]);
			
			String subsubResourceName = com.liyang.jpa.restful.core.utils.CommonUtils.subResourceName(subResourceName, split[5]);
			
			Set<String> fields = FileterNullAndTransient(subsubResourceName, requestBody);
			auditLog.setOwnerResource(subResourceName);
			auditLog.setOwnerUuid(split[4]);
			
			HashSet<String> affectedFields = new HashSet<String>();
			for (String str : fields) {
				affectedFields.add(split[5]+"."+str);
			}
			
			Map fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(),
					"uuid=" + auditLog.getOwnerUuid() + "&fields=" + String.join(",", affectedFields));
			
			context.put("affectedFields", String.join(",", affectedFields));
			context.put("oldMap", fetchOne);
			
		}

		return true;
	}

	@Override
	public HTTPPostOkResponse postHandle(String requestPath, HTTPPostOkResponse httpPostOkResponse,
			Map<Object, Object> context) {

		String affectedFields = (String) context.get("affectedFields");
		AuditLog auditLog = (AuditLog) context.get("auditLog");
		auditLog.setUuid(httpPostOkResponse.getUuid());
		Operate operate = auditLog.getOperate();
		if (operate.equals(Operate.CREATE)) {
			auditLog.setOwnerUuid(httpPostOkResponse.getUuid());
		} 

		Map fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(),
				"uuid=" + auditLog.getOwnerUuid() + "&fields=" + affectedFields);
		MapDifference<String, Object> differences = Maps.difference(fetchOne, (Map) context.get("oldMap"));
		Map<String, ValueDifference<Object>> entriesDiffering = differences.entriesDiffering();
		
		HashMap<String,DiffItem> details = new HashMap<String,DiffItem>();
		Set<Entry<String,ValueDifference<Object>>> entrySet = entriesDiffering.entrySet();
		for (Entry<String, ValueDifference<Object>> entry : entrySet) {
			DiffItem diffItem = new DiffItem();
			String key = entry.getKey();
			ValueDifference<Object> difference = entry.getValue();
			Object leftValue = difference.leftValue();
			if(leftValue instanceof List) {
				diffItem.setType(Type.ARRAY);
			}else if(leftValue instanceof Map) {
				diffItem.setType(Type.OBJECT);
			}else {
				diffItem.setType(Type.SIMPLE);
			}
			diffItem.setNewValue(leftValue);
			diffItem.setOldValue(difference.rightValue());
			details.put(key, diffItem);
		}
		auditLog.setDifference(details);
		auditService.add(auditLog);
		return httpPostOkResponse;
	}

	private Set<String> FileterNullAndTransient(String resource, String requestBody) {
		EntityStructure structure = JpaSmartQuerySupport.getStructure(resource);
		Map<String, Object> objectMap = com.liyang.jpa.restful.core.utils.CommonUtils.stringToMap(requestBody);
		HashMap<String, Object> ret = new HashMap<String, Object>();
		Set<Entry<String, Object>> entrySet = objectMap.entrySet();
		for (Entry<String, Object> entry : entrySet) {
			if (entry.getValue() != null) {
				if (structure.getObjectFields().containsKey(entry.getKey())
						|| structure.getSimpleFields().containsKey(entry.getKey())) {
					ret.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return ret.keySet();
	}


}
