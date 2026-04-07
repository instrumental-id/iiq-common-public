<%-- This page is a custom jsp page that will launch the quicklink for viewing a full identity from a custom workflow.

 The inputs is a JSON object encrypted by IIQ. The JSON object has the following fields:
 {
        "target": "the target identity name",
        "launcher": "the name of the user launching the quicklink",
        "quicklinkName": "the name of the Quicklink",
        "timestamp": "the timestamp of the request"
 }

 The timestamp will, eventually, be used to reject keys older than a certain timeframe.

 Example: http://localhost:8080/identityiq/ui/quicklinkLaunch.jsp?key=2:ACP:O1/t45yw5I3ToxmDyG6/yFP4qgBuEoSgpqFWhkONjM2=
--%>

<%@ page import="java.util.*"%>
<%@ page import="com.identityworksllc.iiq.common.JSPUtils"%>
<%@ page import="com.identityworksllc.iiq.common.logging.SLogger"%>
<%@ page import="sailpoint.tools.GeneralException" %>

<%
    SLogger logger = new SLogger("com.identityworksllc.iiq.common.jsp.quicklinkLaunch");
    try {
        JSPUtils jspUtils = new JSPUtils(session);
        String encryptedKey = request.getParameter("key");

        String redirect = jspUtils.launchQuicklink(encryptedKey);

        response.sendRedirect(redirect);
    } catch(GeneralException e) {
        logger.error("Caught an error launching the quicklink", e);
        response.sendError(500);
    }
%>

<div>Redirecting via deep link...</div>