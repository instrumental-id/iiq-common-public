package com.identityworksllc.iiq.common.logging;

import com.identityworksllc.iiq.common.Utilities;
import com.identityworksllc.iiq.common.plugin.CommonPluginUtils;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A custom servlet filter that populates the logging context with request metadata
 * such as request ID, remote IP, session ID, and authenticated user.
 */
public class ContextLoggerServlet implements Filter {
    /**
     * Logger for the ContextLoggerServlet class
     */
    private static final SLogger log = new SLogger(ContextLoggerServlet.class);

    /**
     * MDC key for the session ID associated with the request, if available (a truncated and hashed version
     * of the actual session ID for privacy)
     */
    public static final String MDC_SESSION_ID = "call:sessionId";

    /**
     * Default value to use in the logging context when the remote IP or user cannot be determined
     */
    public static final String DEFAULT_UNKNOWN = "unknown";

    /**
     * The name of the Identity attribute to use for the user in the logging context
     */
    private String nameAttribute;

    /**
     * Default constructor that initializes the nameAttribute to "name" (Identity.name) by default
     */
    public ContextLoggerServlet() {
        this.nameAttribute = "name";
    }

    /**
     * Populates thread context with request metadata before delegating to the filter chain.
     *
     * Sets the request ID, remote IP, session ID (if available), and authenticated user
     * in the logging context. All context values are cleaned up after the request completes.
     *
     * @param request the servlet request
     * @param response the servlet response
     * @param chain the filter chain
     * @throws IOException if an I/O error occurs
     * @throws ServletException if the request cannot be processed
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String uuid = UUID.randomUUID().toString();
        try (MDC.MDCContext mdcContext = MDC.start(uuid, this::getLoggableName)) {
            mdcContext.put(LoggingConstants.LOG_CTX_ID, uuid);
            if (request instanceof HttpServletRequest) {
                HttpServletRequest hsr = (HttpServletRequest) request;
                mdcContext.put(LoggingConstants.LOG_CLIENT_IP, CommonPluginUtils.getClientIP(hsr).orElse(DEFAULT_UNKNOWN));

                var session = hsr.getSession(false);
                if (session != null) {
                    var sessionId = session.getId();
                    if (Util.isNotNullOrEmpty(sessionId)) {
                        var truncatedSession = sessionId.length() > 7 ? sessionId.substring(0, 7) : sessionId;
                        var truncatedSessionHash = Objects.hashCode(truncatedSession);
                        mdcContext.put(MDC_SESSION_ID, String.valueOf(Math.abs(truncatedSessionHash)));
                    }
                }
            }

            try {
                SailPointContext context = SailPointFactory.getCurrentContext();
                if (context != null && Util.isNotNullOrEmpty(context.getUserName())) {
                    var userName = context.getUserName();
                    if (Util.isNullOrEmpty(nameAttribute)) {
                        nameAttribute = "name";
                    }
                    if (Util.nullSafeEq(nameAttribute, "name")) {
                        mdcContext.identityUnknown(LoggingConstants.LOG_MDC_USER);
                    } else {
                        QueryOptions qo = new QueryOptions();
                        qo.addFilter(sailpoint.object.Filter.eq("name", userName));
                        qo.setResultLimit(1);
                        qo.setCacheResults(false);
                        qo.setCloneResults(true);

                        List<Identity> identities = context.getObjects(Identity.class, qo);
                        if (identities != null && !identities.isEmpty()) {
                            mdcContext.identity(LoggingConstants.LOG_MDC_USER, identities.get(0));
                        } else {
                            mdcContext.put(LoggingConstants.LOG_MDC_USER, userName);
                        }
                    }
                } else {
                    mdcContext.identityUnknown(LoggingConstants.LOG_MDC_USER);
                }
            } catch (GeneralException e) {
                throw new ServletException("Failed to get SailPoint context for logging filter", e);
            }

            chain.doFilter(request, response);
        }
    }

    public String getLoggableName(Identity identity) throws GeneralException {
        String attributeValue = Util.otoa(Utilities.getProperty(identity, nameAttribute));
        if (Util.isNotNullOrEmpty(attributeValue)) {
            return attributeValue;
        } else {
            return identity.getName();
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String nameAttribute = filterConfig.getInitParameter("nameAttribute");
        if (nameAttribute != null && !nameAttribute.isEmpty()) {
            this.nameAttribute = nameAttribute;
        }
    }
}
