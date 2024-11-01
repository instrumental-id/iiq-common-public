package com.identityworksllc.iiq.common.task.export;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.Properties;

/**
 * A record / vo class used to pass connection info to the export partition. Your driver
 * should already have been registered here.
 */
@Getter
@Setter
public final class ExportConnectionInfo implements Serializable {
    /**
     * The driver class (currently not used)
     */
    private String driver;
    /**
     * The encrypted password - will fail if not encrypted
     */
    private String encryptedPassword;
    /**
     * JDBC network timeout
     */
    private long networkTimeout;
    /**
     * Any additional driver options, unique per driver
     */
    private Properties options;

    /**
     * The JDBC URL string
     */
    private String url;

    /**
     * The username to connect
     */
    private String username;

    /**
     * Construct an empty connection info
     */
    public ExportConnectionInfo() {
        this.options = new Properties();
    }

    /**
     * Construct a connection info with the given URL, username, and password
     * @param url The URL
     * @param username The username
     * @param encryptedPassword The encrypted password
     */
    public ExportConnectionInfo(String url, String username, String encryptedPassword) {
        this();

        if (!Objects.requireNonNull(encryptedPassword, "encryptedPassword").contains(":ACP:")) {
            throw new IllegalArgumentException("The password must in IIQ encrypted format");
        }

        this.url = Objects.requireNonNull(url, "url");
        this.username = Objects.requireNonNull(username, "username");
        this.encryptedPassword = encryptedPassword;
    }

    /**
     * Construct a connection info with the given URL, username, and password
     * @param url The URL
     * @param username The username
     * @param encryptedPassword The encrypted password
     * @param options The driver-specific connection options
     */
    public ExportConnectionInfo(String url, String username, String encryptedPassword, Properties options) {
        this();

        if (!Objects.requireNonNull(encryptedPassword, "encryptedPassword").contains(":ACP:")) {
            throw new IllegalArgumentException("The password must in IIQ encrypted format");
        }

        this.url = Objects.requireNonNull(url, "url");
        this.username = Objects.requireNonNull(username, "username");
        this.encryptedPassword = encryptedPassword;
        this.options = options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExportConnectionInfo that = (ExportConnectionInfo) o;
        return Objects.equals(driver, that.driver) && Objects.equals(encryptedPassword, that.encryptedPassword) && Objects.equals(options, that.options) && Objects.equals(url, that.url) && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(driver, encryptedPassword, options, url, username);
    }

}
