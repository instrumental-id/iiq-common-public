package com.identityworksllc.iiq.common.reporting;

import com.identityworksllc.iiq.common.Utilities;
import net.sf.jasperreports.engine.JRException;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.reporting.ReportHelper;
import sailpoint.reporting.datasource.DataSourceColumnHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;

import java.util.*;

/**
 * A somewhat enhanced version of the built-in Filter data source, allowing dot-walking
 * of attributes that are not available as a projection. You can do all of this with
 * the regular Filter data source, as long as you use a combination of subqueries and
 * scripts.
 */
public class DotWalkFilterDataSource extends AbstractJavaDataSource {
    private DataSourceColumnHelper columnHelper;
    private IncrementalObjectIterator<? extends SailPointObject> iterator;
    private LiveReport report;
    private SailPointObject currentRow;
    private SailPointContext context;

    @Override
    public void close() {
        if (iterator != null) {
            Util.flushIterator(iterator);
        }
    }

    /**
     * Gets the column config for the given field
     * @param field The field to fetch
     * @return The column for that field
     */
    protected ReportColumnConfig customGetColumnConfig(String field) {
        for (ReportColumnConfig column : Util.safeIterable(report.getGridColumns())) {
            if (column.getField().equals(field)) {
                return column;
            }
        }
        return null;
    }

    @Override
    public Object getFieldValue(String field) throws GeneralException {
        Object val = null;
        if (this.currentRow != null) {
            ReportColumnConfig col = this.customGetColumnConfig(field);
            val = Utilities.getProperty(currentRow, col.getProperty());
            if (val != null && col.getValueClass() != null && val instanceof String) {
                String valueClass = col.getValueClass();
                String strVal = (String)val;
                if (valueClass.equalsIgnoreCase("xml")) {
                    val = AbstractXmlObject.parseXml(context, strVal);
                } else if (valueClass.equalsIgnoreCase("date")) {
                    if (strVal.length() == 13) {
                        long timestamp = Long.parseLong(strVal);
                        val = new Date(timestamp);
                    }
                } else {
                    Class<?> sailpointClass = ObjectUtil.getSailPointClass(valueClass);
                    if (sailpointClass != null) {
                        val = context.getObject((Class<SailPointObject>) sailpointClass, strVal);
                    }
                }
            }
            if (col.getRenderDef() != null) {
                Map<String, Object> scriptArgs = new HashMap<>();
                scriptArgs.put("item", currentRow);
                val = this.columnHelper.runColumnRenderer(context, col, val, scriptArgs);
            }
            if (val instanceof Date) {
                val = columnHelper.formatDate((Date)val, col);
            }
            val = columnHelper.getColumnValue(val, col);
        }
        return val;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void initialize(SailPointContext sailPointContext, LiveReport liveReport, Attributes<String, Object> attributes, String s, List<Sort> list) throws GeneralException {
        ReportHelper reportHelper = new ReportHelper(sailPointContext, Locale.getDefault(), TimeZone.getDefault());
        QueryOptions queryOptions = reportHelper.getFilterQueryOps(liveReport, attributes);
        Class<? extends SailPointContext> queryClass = (Class<? extends SailPointContext>) liveReport.getDataSource().getObjectClass();
        this.report = liveReport;
        this.context = sailPointContext;
        this.columnHelper = new DataSourceColumnHelper(Locale.getDefault(), TimeZone.getDefault());
        this.iterator = new IncrementalObjectIterator(sailPointContext, queryClass, queryOptions);
    }

    /**
     * Tries to position the cursor on the next element in the data source.
     *
     * @return true if there is a next record, false otherwise
     * @throws JRException if any error occurs while trying to move to the next element
     */
    @Override
    public boolean next() throws JRException {
        if (iterator.hasNext()) {
            currentRow = iterator.next();
            return true;
        }
        return false;
    }
}
