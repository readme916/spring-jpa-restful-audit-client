package com.liyang.jpa.audit.client.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
		return "审计系统会把修改历史，传到专用记录服务器";
	}

	@Override
	public String name() {
		// TODO Auto-generated method stub
		return "审计系统拦截器";
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
			context.put("affectedFields", affectedFields);
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
			context.put("affectedFields", affectedFields);
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
				affectedFields.add(split[3] + "." + str);
			}
			String bodyUuid = getUuid(requestBody);
			Map fetchOne;
			
			if (bodyUuid != null) {
				fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(), "uuid=" + auditLog.getOwnerUuid()
						+ "&" + split[3] + ".uuid=" + bodyUuid + "&fields=" + String.join(",", affectedFields));
				if(fetchOne.equals(Collections.EMPTY_MAP)) {
					fetchOne = new HashMap();
					fetchOne.put(split[3], null);
				}
			} else {
				fetchOne = new HashMap();
				fetchOne.put(split[3], null);
			}
			context.put("affectedFields", affectedFields);
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

			context.put("affectedFields", affectedFields);
			context.put("oldMap", fetchOne);

		} else if (matcher.match("/*/*/*/*/*", requestPath)) {
			// 桥接桥接创建
			auditLog.setLinkType(LinkType.BRIDGE);
			auditLog.setOperate(Operate.LINK_CREATE);

			String subResourceName = com.liyang.jpa.restful.core.utils.CommonUtils.subResourceName(split[1], split[3]);

			String subsubResourceName = com.liyang.jpa.restful.core.utils.CommonUtils.subResourceName(subResourceName,
					split[5]);

			Set<String> fields = FileterNullAndTransient(subsubResourceName, requestBody);
			auditLog.setOwnerResource(subResourceName);
			auditLog.setOwnerUuid(split[4]);

			HashSet<String> affectedFields = new HashSet<String>();
			for (String str : fields) {
				affectedFields.add(split[5] + "." + str);
			}

			String bodyUuid = getUuid(requestBody);
			Map fetchOne;
			if (bodyUuid != null) {
				fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(), "uuid=" + auditLog.getOwnerUuid()
						+ "&" + split[5] + ".uuid=" + bodyUuid + "&fields=" + String.join(",", affectedFields));
				if(fetchOne.equals(Collections.EMPTY_MAP)) {
					fetchOne = new HashMap();
					fetchOne.put(split[5], null);
				}
			} else {
				fetchOne = new HashMap();
				fetchOne.put(split[5], null);
			}

			context.put("affectedFields", affectedFields);
			context.put("oldMap", fetchOne);

		}

		return true;
	}

	@Override
	public HTTPPostOkResponse postHandle(String requestPath, HTTPPostOkResponse httpPostOkResponse,
			Map<Object, Object> context) {

		Set fields = (Set) context.get("affectedFields");
		String affectedFields = String.join(",", fields);

		AuditLog auditLog = (AuditLog) context.get("auditLog");
		auditLog.setUuid(httpPostOkResponse.getUuid());
		Operate operate = auditLog.getOperate();
		if (operate.equals(Operate.CREATE)) {
			auditLog.setOwnerUuid(httpPostOkResponse.getUuid());
		}

		Map fetchOne;
		if (operate.equals(Operate.LINK_CREATE)) {
			PathMatcher matcher = new AntPathMatcher();
			String prefix = "";
			String[] split = requestPath.split("/");
			if (matcher.match("/*/*/*/*/*", requestPath)) {
				prefix = split[5];
			} else if (matcher.match("/*/*/*", requestPath)) {
				prefix = split[3];
			}

			fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(), "uuid=" + auditLog.getOwnerUuid() + "&"
					+ prefix + ".uuid=" + httpPostOkResponse.getUuid() + "&fields=" + affectedFields);
		} else {

			fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(),
					"uuid=" + auditLog.getOwnerUuid() + "&fields=" + affectedFields);
		}

		MapDifference<String, Object> differences = Maps.difference(fetchOne, (Map) context.get("oldMap"));
		Map<String, ValueDifference<Object>> entriesDiffering = differences.entriesDiffering();

		HashMap<String, DiffItem> details = parseDiffer(entriesDiffering);

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

	private HashMap<String, DiffItem> parseDiffer(Map<String, ValueDifference<Object>> entriesDiffering) {
		HashMap<String, DiffItem> details = new HashMap<String, DiffItem>();
		Set<Entry<String, ValueDifference<Object>>> entrySet = entriesDiffering.entrySet();
		for (Entry<String, ValueDifference<Object>> entry : entrySet) {
			DiffItem diffItem = new DiffItem();
			String key = entry.getKey();
			ValueDifference<Object> difference = entry.getValue();
			Object leftValue = difference.leftValue();
			Object rightValue = difference.rightValue();
			if (leftValue instanceof List) {
				diffItem.setType(Type.ARRAY);
				if (rightValue instanceof LinkedHashMap) {
					rightValue = new ArrayList();
				}
				Map<String, List> compareResult = compare((List) leftValue, (List) rightValue);

				diffItem.setNewValue(compareResult.get("new"));
				diffItem.setOldValue(compareResult.get("old"));

			} else if (leftValue instanceof Map) {
				diffItem.setType(Type.OBJECT);
				if(leftValue.equals(Collections.EMPTY_MAP)) {
					leftValue = null;
				}
				if(rightValue.equals(Collections.EMPTY_MAP)) {
					rightValue = null;
				}
				diffItem.setNewValue(leftValue);
				diffItem.setOldValue(rightValue);
			} else {
				diffItem.setType(Type.SIMPLE);
				diffItem.setNewValue(leftValue);
				diffItem.setOldValue(rightValue);
			}

			details.put(key, diffItem);
		}
		return details;
	}

	private Map<String, List> compare(List leftValue, List rightValue) {
		if (leftValue == null) {
			leftValue = new ArrayList();
		}
		if (rightValue == null) {
			rightValue = new ArrayList();
		}
		Map leftUuidMap = new HashMap<String, Object>();
		for (Object leftObject : leftValue) {
			Map left = (Map) leftObject;
			leftUuidMap.put(left.get("uuid").toString(), left);
		}
		Map rightUuidMap = new HashMap<String, Object>();
		for (Object rightObject : rightValue) {
			Map right = (Map) rightObject;
			rightUuidMap.put(right.get("uuid").toString(), right);
		}
		HashSet<String> leftKeySet = new HashSet<String>(leftUuidMap.keySet());
		HashSet<String> rightKeySet = new HashSet<String>(rightUuidMap.keySet());
		leftKeySet.removeAll(rightKeySet);
		HashSet<String> newSet = leftKeySet;
		HashSet<String> leftKeySet2 = new HashSet<String>(leftUuidMap.keySet());
		HashSet<String> rightKeySet2 = new HashSet<String>(rightUuidMap.keySet());
		rightKeySet2.removeAll(leftKeySet2);
		HashSet<String> oldSet = rightKeySet2;

		HashMap<String, List> ret = new HashMap<String, List>();
		for (String str : oldSet) {
			List old = (List) ret.getOrDefault("old", new ArrayList());
			ret.put("old", old);
			old.add(rightUuidMap.get(str));
		}
		for (String str : newSet) {
			List n = (List) ret.getOrDefault("new", new ArrayList());
			ret.put("new", n);
			n.add(leftUuidMap.get(str));
		}
		return ret;
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

	private String getUuid(String requestBody) {
		Map<String, Object> objectMap = com.liyang.jpa.restful.core.utils.CommonUtils.stringToMap(requestBody);
		if (objectMap.containsKey("uuid")) {
			return objectMap.get("uuid").toString();
		} else {
			return null;
		}
	}

}
