package com.dianping.cat.system.alarm.threshold.listener;

import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;

import com.dianping.cat.Cat;
import com.dianping.cat.helper.CatString;
import com.dianping.cat.helper.TimeUtil;
import com.dianping.cat.system.alarm.alert.AlertInfo;
import com.dianping.cat.system.alarm.alert.AlertManager;
import com.dianping.cat.system.alarm.threshold.event.ThresholdAlertEvent;
import com.dianping.cat.system.alarm.threshold.template.ThresholdAlarmMeta;
import com.dianping.cat.system.event.Event;
import com.dianping.cat.system.event.EventListener;
import com.dianping.cat.system.event.EventType;
import com.dianping.cat.system.notify.ReportRenderImpl;
import com.dianping.cat.system.page.alarm.RuleManager;
import com.site.lookup.annotation.Inject;

import freemarker.template.Configuration;
import freemarker.template.Template;

public class ThresholdAlertListener implements EventListener, Initializable {
	@Inject
	private AlertManager m_alertManager;

	public Configuration m_configuration;

	@Inject
	private RuleManager m_ruleManager;

	private String buildAlarmContent(ThresholdAlarmMeta meta) {
		Map<Object, Object> root = new HashMap<Object, Object>();
		StringWriter sw = new StringWriter(5000);

		root.put("domain", meta.getDomain());
		root.put("date", meta.getDate());
		root.put("url", buildProblemUrl(meta.getBaseUrl(), meta.getDomain(), meta.getDate()));

		try {
			String type = meta.getType();

			if (type.equalsIgnoreCase(AlertInfo.EXCEPTION)) {
				Template t = m_configuration.getTemplate("exceptionAlarm.ftl");

				t.process(root, sw);
			} else if (type.equalsIgnoreCase(AlertInfo.SERVICE)) {
				Template t = m_configuration.getTemplate("serviceAlarm.ftl");

				t.process(root, sw);
			}
		} catch (Exception e) {
			Cat.logError(e);
		}
		return sw.toString();
	}

	private String buildAlarmTitle(ThresholdAlarmMeta meta) {
		String type = meta.getType();

		if (type.equalsIgnoreCase(AlertInfo.EXCEPTION)) {
			return CatString.EXCEPTION + "[ " + String.valueOf(meta.getDomain()) + " ]";
		} else if (type.equalsIgnoreCase(AlertInfo.SERVICE)) {
			return CatString.SERVICE + "[ " + String.valueOf(meta.getDomain()) + " ]";
		}

		return "Default";
	}

	private String buildProblemUrl(String baseUrl, String domain, Date date) {
		long time = date.getTime();

		time = time - time % TimeUtil.ONE_HOUR;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHH");

		baseUrl = baseUrl.replaceAll("dashboard", "p");
		StringBuilder sb = new StringBuilder(baseUrl);

		sb.append("?").append("domain=").append(domain).append("&date=").append(sdf.format(new Date(time)));
		return sb.toString();
	}

	@Override
	public void initialize() throws InitializationException {
		m_configuration = new Configuration();

		m_configuration.setDefaultEncoding("UTF-8");
		try {
			m_configuration.setClassForTemplateLoading(ReportRenderImpl.class, "/freemaker");
		} catch (Exception e) {
			Cat.logError(e);
		}
	}

	@Override
	public boolean isEligible(Event event) {
		if (event.getEventType() == EventType.AlertEvent) {
			return true;
		}
		return false;
	}

	@Override
	public void onEvent(Event event) {
		ThresholdAlertEvent alertEvent = (ThresholdAlertEvent) event;

		ThresholdAlarmMeta meta = alertEvent.getAlarmMeta();
		String title = buildAlarmTitle(meta);
		String content = buildAlarmContent(meta);
		String alertType = meta.getDuration().getAlarm().toLowerCase();
		String ruleType = meta.getType();

		if (alertType != null && alertType.length() > 0) {
			String[] types = alertType.split(",");

			for (String type : types) {
				AlertInfo info = new AlertInfo();

				info.setContent(content);
				info.setTitle(title);
				info.setRuleId(meta.getRuleId());
				info.setDate(meta.getDate());
				info.setRuleType(ruleType);

				if (type.equalsIgnoreCase(AlertInfo.EMAIL)) {
					List<String> address = m_ruleManager.queryUserMailsByRuleId(meta.getRuleId());

					info.setAlertType(AlertInfo.EMAIL_TYPE);
					info.setMails(address);
					m_alertManager.addAlarmInfo(info);
				} else if (type.equalsIgnoreCase(AlertInfo.SMS)) {
					List<String> address = m_ruleManager.queryUserPhonesByRuleId(meta.getRuleId());

					info.setAlertType(AlertInfo.SMS_TYPE);
					info.setPhones(address);
					m_alertManager.addAlarmInfo(info);
				}
			}
		}
	}

}
