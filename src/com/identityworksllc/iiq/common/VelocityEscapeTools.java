package com.identityworksllc.iiq.common;

import org.apache.commons.lang.StringEscapeUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Velocity has its own 'escape tools' package, but it is not included with
 * IIQ, so we made our own
 */
public class VelocityEscapeTools {
    /**
     * @see StringEscapeUtils#escapeCsv(String)
     */
    public String csv(String input) {
        return StringEscapeUtils.escapeCsv(input);
    }

    /**
     * @see StringEscapeUtils#escapeHtml(String)
     */
    public String html(String input) {
        return StringEscapeUtils.escapeHtml(input);
    }

    /**
     * @see StringEscapeUtils#escapeJava(String)
     */
    public String java(String input) {
        return StringEscapeUtils.escapeJava(input);
    }

    /**
     * @see StringEscapeUtils#escapeSql(String) 
     */
    public String sql(String input) {
        return StringEscapeUtils.escapeSql(input);
    }

    /**
     * @see URLEncoder#encode(String, String) 
     */
    public String url(String input) throws UnsupportedEncodingException {
        return URLEncoder.encode(input, "UTF-8");
    }

    /**
     * @see StringEscapeUtils#escapeXml(String)
     */
    public String xml(String input) {
        return StringEscapeUtils.escapeXml(input);
    }

}
