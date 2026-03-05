package com.identityworksllc.iiq.common.auth;

import sailpoint.object.Identity;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.*;

/**
 * This class provides minimal implementation of HttpServletRequest, primarily to support
 * the session-based retrieval of the logged-in user by IIQ's BaseResource. In particular,
 * the logged-in user must be stored in the session attribute 'principal', and the rest of
 * this is just scaffolding to support that one-liner.
 */
public class DummyRequest implements HttpServletRequest {
    /**
     * The dummy session associated with this request
     */
    private final DummyHttpSession session;

    /**
     * Constructs a new DummyRequest with the given logged-in user.
     * A DummyHttpSession is automatically created for this request.
     *
     * @param loggedInUser The Identity to use as the logged-in user
     */
    public DummyRequest(Identity loggedInUser) {
        this.session = new DummyHttpSession();

        if (loggedInUser != null) {
            this.session.setAttribute("principal", loggedInUser.getName());
        }
    }

    @Override
    public boolean authenticate(javax.servlet.http.HttpServletResponse response) throws IOException, ServletException {
        return false;
    }

    /**
     * Change the session ID (no-op in this dummy implementation).
     */
    @Override
    public String changeSessionId() {
        return session.getId();
    }

    @Override
    public javax.servlet.AsyncContext getAsyncContext() {
        return null;
    }

    @Override
    public Object getAttribute(String name) {
        return null;
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return null;
    }

    @Override
    public String getAuthType() {
        return "BASIC";
    }

    @Override
    public String getCharacterEncoding() {
        return "UTF-8";
    }

    @Override
    public int getContentLength() {
        return 0;
    }

    @Override
    public long getContentLengthLong() {
        return 0;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public String getContextPath() {
        return null;
    }

    @Override
    public Cookie[] getCookies() {
        return new Cookie[0];
    }

    @Override
    public long getDateHeader(String name) {
        return 0;
    }

    @Override
    public javax.servlet.DispatcherType getDispatcherType() {
        return null;
    }

    @Override
    public String getHeader(String name) {
        return null;
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return null;
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return null;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return null;
    }

    @Override
    public int getIntHeader(String name) {
        return 0;
    }

    @Override
    public String getLocalAddr() {
        return null;
    }

    @Override
    public String getLocalName() {
        return null;
    }

    @Override
    public int getLocalPort() {
        return 0;
    }

    @Override
    public Locale getLocale() {
        return null;
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return null;
    }

    @Override
    public String getMethod() {
        return null;
    }

    @Override
    public String getParameter(String name) {
        return null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return new HashMap<>();
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return null;
    }

    @Override
    public String[] getParameterValues(String name) {
        return new String[0];
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        return null;
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        return List.of();
    }

    @Override
    public String getPathInfo() {
        return null;
    }

    @Override
    public String getPathTranslated() {
        return null;
    }

    @Override
    public String getProtocol() {
        return null;
    }

    @Override
    public String getQueryString() {
        return null;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return null;
    }

    @Override
    public String getRealPath(String path) {
        return null;
    }

    @Override
    public String getRemoteAddr() {
        return null;
    }

    @Override
    public String getRemoteHost() {
        return null;
    }

    @Override
    public int getRemotePort() {
        return 0;
    }

    @Override
    public String getRemoteUser() {
        return null;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }

    @Override
    public String getRequestURI() {
        return null;
    }

    @Override
    public StringBuffer getRequestURL() {
        return null;
    }

    @Override
    public String getRequestedSessionId() {
        return null;
    }

    @Override
    public String getScheme() {
        return null;
    }

    @Override
    public String getServerName() {
        return null;
    }

    @Override
    public int getServerPort() {
        return 0;
    }

    @Override
    public ServletContext getServletContext() {
        return null;
    }

    @Override
    public String getServletPath() {
        return "";
    }

    /**
     * Get the HttpSession associated with this request.
     * This returns the DummyHttpSession which supports getting and setting attributes.
     */
    @Override
    public HttpSession getSession() {
        return session;
    }

    /**
     * Get the HttpSession associated with this request, optionally creating one.
     */
    @Override
    public HttpSession getSession(boolean create) {
        return session;
    }

    /**
     * Get the session attributes.
     * This allows convenient access to the underlying session state.
     *
     * @return The underlying map of session attributes
     */
    public Map<String, Object> getSessionAttributes() {
        return session.getAttributeMap();
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public boolean isAsyncStarted() {
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return false;
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public boolean isUserInRole(String role) {
        return false;
    }

    @Override
    public void login(String username, String password) throws ServletException {
        // No-op
    }

    @Override
    public void logout() throws ServletException {
        // No-op
    }

    @Override
    public void removeAttribute(String name) {
        // No-op
    }

    @Override
    public void setAttribute(String name, Object o) {
        // No-op
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        // No-op
    }

    @Override
    public javax.servlet.AsyncContext startAsync() throws IllegalStateException {
        return null;
    }

    @Override
    public javax.servlet.AsyncContext startAsync(javax.servlet.ServletRequest servletRequest, javax.servlet.ServletResponse servletResponse) throws IllegalStateException {
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
        return null;
    }
}
