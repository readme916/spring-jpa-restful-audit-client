package com.liyang.jpa.audit.client.common;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.liyang.jpa.audit.server.domain.AuditLog.Terminal;

public class CommonUtils {

	public static String getIP() {
		
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
		
		String ip = request.getHeader("x-forwarded-for");
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("X-Real-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		return ip;
	}

	public static Terminal getTerminal() {
		HttpServletRequest request = ((ServletRequestAttributes) (RequestContextHolder.currentRequestAttributes()))
				.getRequest();
		String terminal = request.getHeader("terminal");
		if (terminal != null) {
			return Terminal.valueOf(terminal.toUpperCase());
		} else {
			String ua = request.getHeader("User-Agent");
			if (ua.indexOf("wxwork") != -1) {
				if (ua.indexOf("Android") != -1 || ua.indexOf("iPhone") != -1) {
					return Terminal.WXWORK_MOBILE;
				} else {
					return Terminal.WXWORK_DESK;
				}
			} else if (ua.indexOf("MicroMessenger") != -1) {
				if (ua.contains("WindowsWechat")) {
					return Terminal.WXPUBLIC_DESK;
				} else {
					return Terminal.WXPUBLIC_MOBILE;
				}
			} else if (ua.contains("Electron")) {
				return Terminal.ELECTRON;
			} else {
				return Terminal.BROWSER;
			}
		}
	}
}
