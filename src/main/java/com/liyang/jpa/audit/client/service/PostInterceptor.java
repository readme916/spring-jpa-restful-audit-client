package com.liyang.jpa.audit.client.service;

import java.security.Principal;
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

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.liyang.jpa.restful.core.interceptor.JpaRestfulPostInterceptor;
import com.liyang.jpa.restful.core.response.HTTPPostOkResponse;
import com.liyang.jpa.smart.query.db.SmartQuery;
import com.liyang.jpa.smart.query.db.structure.EntityStructure;

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
	public String[] path() {
		// TODO Auto-generated method stub
		return new String[] { "/**" };
	}

	@Override

	public boolean preHandle(String requestPath, Map<String, Object> requestBody, Object ownerInstance,
			Map<String, Object> context) {
		AuditLog auditLog = new AuditLog();
		context.put("auditLog", auditLog);
		PathMatcher matcher = new AntPathMatcher();

		auditLog.setApplication(application);
		auditLog.setPostBody(requestBody);
		auditLog.setRequestPath(requestPath);
		auditLog.setIp(CommonUtils.getIPAddress());
		auditLog.setCreatedAt(new Date());

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

		String[] split = requestPath.split("/");
		if (matcher.match("/*", requestPath)) {
			// 创建
			HashMap<String, Object> affectedFields = FileterNullAndTransient(split[1], requestBody);
			auditLog.setResource(split[1]);
			auditLog.setLinkType(LinkType.DIRECT);
			auditLog.setEvent("create");
			HashMap<String, Object> oldMap = new HashMap<String, Object>();
			for (String key : affectedFields.keySet()) {
				oldMap.put(key, null);
			}
			context.put("affectedFields", affectedFields.keySet());
			context.put("oldMap", oldMap);

		} else if (matcher.match("/*/*", requestPath)) {
			// 更新
			HashMap<String, Object> affectedFields = FileterNullAndTransient(split[1], requestBody);
			auditLog.setResource(split[1]);
			auditLog.setUuid(split[2]);
			auditLog.setLinkType(LinkType.DIRECT);

			if (getEvent(requestBody) != null) {
				auditLog.setEvent(getEvent(requestBody));
			} else {
				auditLog.setEvent("update");
			}
			Map fetchOne = (Map) SmartQuery.fetchOne(auditLog.getResource(),
					"uuid=" + auditLog.getUuid() + "&fields=" + String.join(",", affectedFields.keySet()));
			context.put("affectedFields", affectedFields.keySet());
			context.put("oldMap", fetchOne);

		} else if (matcher.match("/*/*/*", requestPath)) {
			// 桥接创建
			auditLog.setLinkType(LinkType.BRIDGE);
			String subResourceName = com.liyang.jpa.restful.core.utils.CommonUtils.subResourceName(split[1], split[3]);
			HashMap<String, Object> fields = FileterNullAndTransient(subResourceName, requestBody);
			
			auditLog.setResource(split[1]);
			auditLog.setUuid(split[2]);
			auditLog.setSubResource(split[3]);

			HashSet<String> affectedFields = new HashSet<String>();
			for (String str : fields.keySet()) {
				affectedFields.add(split[3] + "." + str);
			}
			String bodyUuid = getUuid(requestBody);
			Map fetchOne;
			fetchOne = (Map) SmartQuery.fetchOne(auditLog.getResource(), "uuid=" + auditLog.getUuid() + "&fields=" + String.join(",", affectedFields));
			if (fetchOne.equals(Collections.EMPTY_MAP)) {
				fetchOne = new HashMap();
				fetchOne.put(split[3], new HashMap());
			}

			if (bodyUuid != null) {
				auditLog.setEvent("link");
			} else {
				auditLog.setEvent("create");
			}
			context.put("affectedFields", affectedFields);
			context.put("oldMap", fetchOne);

		} else if (matcher.match("/*/*/*/*", requestPath)) {
			// 桥接更新
			auditLog.setLinkType(LinkType.BRIDGE);
			String subResourceName = com.liyang.jpa.restful.core.utils.CommonUtils.subResourceName(split[1], split[3]);
			HashMap<String, Object> affectedFields = FileterNullAndTransient(subResourceName, requestBody);
			if (getEvent(requestBody) != null) {
				auditLog.setEvent(getEvent(requestBody));
			} else {
				auditLog.setEvent("update");
			}

			auditLog.setResource(split[1]);
			auditLog.setUuid(split[2]);
			auditLog.setSubResource(split[3]);
			auditLog.setSubResourceId(split[4]);
			Map fetchOne = (Map) SmartQuery.fetchOne(subResourceName,
					"uuid=" + auditLog.getSubResourceId() + "&fields=" + String.join(",", affectedFields.keySet()));

			context.put("affectedFields", affectedFields.keySet());
			context.put("oldMap", fetchOne);

		} 
//		else if (matcher.match("/*/*/*/*/*", requestPath)) {
//			// 桥接桥接创建
//			auditLog.setLinkType(LinkType.BRIDGE);
//			String subResourceName = com.liyang.jpa.restful.core.utils.CommonUtils.subResourceName(split[1], split[3]);
//			String subsubResourceName = com.liyang.jpa.restful.core.utils.CommonUtils.subResourceName(subResourceName,
//					split[5]);
//
//			HashMap<String, Object> fields = FileterNullAndTransient(subsubResourceName, requestBody);
//			auditLog.setEvent("link");
//			auditLog.setOwnerResource(subResourceName);
//			auditLog.setOwnerUuid(split[4]);
//
//			HashSet<String> affectedFields = new HashSet<String>();
//			for (String str : fields.keySet()) {
//				affectedFields.add(split[5] + "." + str);
//			}
//
//			String bodyUuid = getUuid(requestBody);
//			Map fetchOne;
//			if (bodyUuid != null) {
//				fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(), "uuid=" + auditLog.getOwnerUuid()
//						+ "&" + split[5] + ".uuid=" + bodyUuid + "&fields=" + String.join(",", affectedFields));
//				if (fetchOne.equals(Collections.EMPTY_MAP)) {
//					fetchOne = new HashMap();
//					fetchOne.put(split[5], null);
//				}
//			} else {
//				fetchOne = new HashMap();
//				fetchOne.put(split[5], null);
//			}
//
//			context.put("affectedFields", affectedFields);
//			context.put("oldMap", fetchOne);
//
//		}

		return true;
	}

	@Override
	public HTTPPostOkResponse postHandle(String requestPath, HTTPPostOkResponse httpPostOkResponse,
			Map<String, Object> context) {

		Set fields = (Set) context.get("affectedFields");
		String affectedFields = String.join(",", fields);

		AuditLog auditLog = (AuditLog) context.get("auditLog");
		String event = auditLog.getEvent();
		if (event.equals("create") && auditLog.getLinkType().equals(LinkType.DIRECT)) {
			auditLog.setUuid(httpPostOkResponse.getUuid());
		}

		Map fetchOne=null;
		PathMatcher matcher = new AntPathMatcher();
		String prefix = "";
		String[] split = requestPath.split("/");
//		if (matcher.match("/*/*/*/*/*", requestPath)) {
//			prefix = split[5];
//			fetchOne = (Map) SmartQuery.fetchOne(auditLog.getOwnerResource(), "uuid=" + auditLog.getOwnerUuid() + "&"
//					+ prefix + ".uuid=" + httpPostOkResponse.getUuid() + "&fields=" + affectedFields);
//		} else 
		if (matcher.match("/*", requestPath)) {
			auditLog.setUuid(httpPostOkResponse.getUuid());
			fetchOne = (Map) SmartQuery.fetchOne(auditLog.getResource(),
					"uuid=" + auditLog.getUuid() + "&fields=" + affectedFields);
			
		}else if(matcher.match("/*/*", requestPath)) {
			fetchOne = (Map) SmartQuery.fetchOne(auditLog.getResource(),
					"uuid=" + auditLog.getUuid() + "&fields=" + affectedFields);
		}else if (matcher.match("/*/*/*", requestPath)) {
			prefix = split[3];
			fetchOne = (Map) SmartQuery.fetchOne(auditLog.getResource(), "uuid=" + auditLog.getUuid() + "&fields=" + affectedFields);
		} else if (matcher.match("/*/*/*/*", requestPath)){
			
			String subResourceName = com.liyang.jpa.restful.core.utils.CommonUtils.subResourceName(split[1], split[3]);
			fetchOne = (Map) SmartQuery.fetchOne(subResourceName,
					"uuid=" + auditLog.getSubResourceId() + "&fields=" + affectedFields);
		}

		MapDifference<String, Object> differences = Maps.difference(fetchOne, (Map) context.get("oldMap"));
		Map<String, ValueDifference<Object>> entriesDiffering = differences.entriesDiffering();

		HashMap<String, DiffItem> details = parseDiffer(entriesDiffering);

		auditLog.setDifference(details);
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
				}else if(rightValue.equals(Collections.EMPTY_MAP) ) {
					rightValue = new ArrayList();
				}
				Map<String, List> compareResult = compare((List) leftValue, (List) rightValue);

				diffItem.setNewValue(compareResult.get("new"));
				diffItem.setOldValue(compareResult.get("old"));

			} else if (leftValue instanceof Map) {
				diffItem.setType(Type.OBJECT);
				if (leftValue == null || leftValue.equals(Collections.EMPTY_MAP)) {
					leftValue = null;
				}
				if (rightValue == null || rightValue.equals(Collections.EMPTY_MAP)) {
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

	private HashMap<String, Object> FileterNullAndTransient(String resource, Map<String, Object> requestBody) {
		EntityStructure structure = SmartQuery.getStructure(resource);
		HashMap<String, Object> ret = new HashMap<String, Object>();
		Set<Entry<String, Object>> entrySet = requestBody.entrySet();
		for (Entry<String, Object> entry : entrySet) {
			if (entry.getValue() != null) {
				if (structure.getObjectFields().containsKey(entry.getKey())
						|| structure.getSimpleFields().containsKey(entry.getKey())) {
					ret.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return ret;
	}

	private String getUuid(Map<String, Object> requestBody) {
		if (requestBody.containsKey("uuid")) {
			return requestBody.get("uuid").toString();
		} else {
			return null;
		}
	}

	private String getEvent(Map<String, Object> requestBody) {

		if (requestBody.containsKey("event")) {
			return requestBody.get("event").toString();
		} else {
			return null;
		}
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
