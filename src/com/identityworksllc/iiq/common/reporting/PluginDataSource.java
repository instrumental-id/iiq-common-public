package com.identityworksllc.iiq.common.reporting;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.Sort;
import sailpoint.plugin.PluginsUtil;
import sailpoint.reporting.datasource.JavaDataSource;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;

import java.util.List;

@SuppressWarnings("unused")
public class PluginDataSource extends AbstractJavaDataSource {

    private JavaDataSource realDataSource;

    @Override
    public void close() {
        realDataSource.close();
    }

    @Override
    public String getBaseHql() {
        return realDataSource.getBaseHql();
    }

    @Override
    public QueryOptions getBaseQueryOptions() {
        return realDataSource.getBaseQueryOptions();
    }

    @Override
    public Object getFieldValue(JRField jrField) throws JRException {
        return realDataSource.getFieldValue(jrField);
    }

    @Override
    public Object getFieldValue(String s) throws GeneralException {
        return realDataSource.getFieldValue(s);
    }

    @Override
    public int getSizeEstimate() throws GeneralException {
        return realDataSource.getSizeEstimate();
    }

    @Override
    public void initialize(SailPointContext sailPointContext, LiveReport liveReport, Attributes<String, Object> attributes, String s, List<Sort> list) throws GeneralException {
        String pluginName = attributes.getString("pluginName");
        String pluginClass = attributes.getString("pluginClass");
        this.realDataSource = PluginsUtil.instantiate(pluginName, pluginClass);
        if (realDataSource == null) {
            throw new GeneralException("Plugin class " + pluginClass + " is not present in plugin " + pluginName);
        }
        this.realDataSource.initialize(sailPointContext, liveReport, attributes, s, list);
    }

    @Override
    public boolean next() throws JRException {
        return realDataSource.next();
    }

    @Override
    public void setLimit(int i, int i1) {
        realDataSource.setLimit(i, i1);
    }

    @Override
    public void setMonitor(Monitor monitor) {
        realDataSource.setMonitor(monitor);
    }
}
