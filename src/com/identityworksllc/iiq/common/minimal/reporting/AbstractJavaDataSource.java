package com.identityworksllc.iiq.common.minimal.reporting;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.ReportDataSource;
import sailpoint.object.Sort;
import sailpoint.reporting.datasource.JavaDataSource;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Parent class for Java data sources, making certain things easier
 */
public abstract class AbstractJavaDataSource implements JavaDataSource {

	protected SailPointContext context;
	protected String groupBy;
	protected Attributes<Object, Object> inputs;
	protected int limit;
	protected LiveReport liveReport;
	protected Monitor monitor;
	protected ArrayList<Object> sortBy;
	protected int start;

	@Override
	public void close() {
		// No-op by default
	}

	@Override
	public String getBaseHql() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public QueryOptions getBaseQueryOptions() {
		// TODO Auto-generated method stub
		return null;
	}

	public Object getFieldValue(JRField jrField) throws JRException {
		String name = jrField.getName();
		Object value = null;
		try {
			value = getFieldValue(name);
		}
		catch (GeneralException e) {
			throw new JRException(e);
		}
		return value;
	}
	
	@Override
	public int getSizeEstimate() throws GeneralException {
		return 0;
	}

	@Override
	public void initialize(SailPointContext context, LiveReport liveReport, Attributes<String, Object> taskArguments, String groupBy, List<Sort> sortBy) throws GeneralException {
		this.context = context;
		this.liveReport = liveReport;
		this.inputs = new Attributes<>();
		if (taskArguments != null) {
			this.inputs.putAll(taskArguments);
		}
		this.groupBy = groupBy;

		this.sortBy = new ArrayList<>();
		if (sortBy != null) {
			this.sortBy.addAll(sortBy);
		}
	}

	@Override
	public void setLimit(int arg0, int arg1) {
		start = arg0;
		limit = arg1;
	}

	@Override
	public void setMonitor(Monitor arg0) {
		this.monitor = arg0;
	}

}
