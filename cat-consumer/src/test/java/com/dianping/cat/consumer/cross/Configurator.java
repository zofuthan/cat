package com.dianping.cat.consumer.cross;

import static com.dianping.cat.Constants.HOUR;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.unidal.lookup.annotation.Inject;
import org.unidal.lookup.configuration.AbstractResourceConfigurator;
import org.unidal.lookup.configuration.Component;

import com.dianping.cat.consumer.MockReportManager;
import com.dianping.cat.consumer.cross.model.entity.CrossReport;
import com.dianping.cat.report.ReportDelegate;
import com.dianping.cat.report.ReportManager;

public class Configurator extends AbstractResourceConfigurator {

	public static void main(String[] args) {
		generatePlexusComponentsXmlFile(new Configurator());
	}

	protected Class<?> getTestClass() {
		return CrossAnalyzerTest.class;
	}

	@Override
	public List<Component> defineComponents() {
		List<Component> all = new ArrayList<Component>();
		final String ID = CrossAnalyzer.ID;

		all.add(C(ReportManager.class, ID, MockCrossReportManager.class)//
		      .req(ReportDelegate.class, ID));
		all.add(C(ReportDelegate.class, ID, ExtendedCrossDelegate.class));

		return all;
	}

	public static class ExtendedCrossDelegate extends CrossDelegate {
	}

	public static class MockCrossReportManager extends MockReportManager<CrossReport> {
		private Map<Long, Map<String, CrossReport>> m_reports = new ConcurrentHashMap<Long, Map<String, CrossReport>>();;

		@Inject
		private ReportDelegate<CrossReport> m_delegate;

		@Override
		public CrossReport getHourlyReport(long startTime, String domain, boolean createIfNotExist) {
			Map<String, CrossReport> reports = m_reports.get(startTime);

			if (reports == null && createIfNotExist) {
				reports = new ConcurrentHashMap<String, CrossReport>();
				m_reports.put(startTime, reports);
			}

			CrossReport report = reports.get(domain);

			if (report == null && createIfNotExist) {
				report = m_delegate.makeReport(domain, startTime, HOUR);
				reports.put(domain, report);
			}
			return report;
		}
	}
}
