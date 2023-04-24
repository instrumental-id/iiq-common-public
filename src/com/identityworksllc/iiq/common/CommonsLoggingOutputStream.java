package com.identityworksllc.iiq.common;

import org.apache.commons.logging.Log;

import java.io.OutputStream;

/**
 * Implements an OutputStream that will write to the given logger whenever any
 * newline is encountered. The output is buffered until a newline.
 */
public class CommonsLoggingOutputStream extends OutputStream {
    private final Level level;

    public enum Level {
        Fatal,
        Error,
        Warn,
        Info,
        Debug,
        Trace
    }

    /**
     * The buffer holding the string we've collected so far
     */
    private String buffer;

    /**
     * The logger
     */
    private final Log logger;

    /**
     * Constructs a new OutputStream that will write to the given logger at the
     * given level whenever a newline is encountered in the byte stream.
     *
     * @param level The log level
     * @param logger The logger to write to
     */
    public CommonsLoggingOutputStream(Level level, Log logger) {
        this.logger = logger;
        this.level = level;
        this.buffer = "";
    }

    /**
     * Writes a byte to the output stream. This method flushes automatically at the end of a line.
     *
     * @see OutputStream#write(int)
     */
    public void write (int b) {
        byte[] bytes = new byte[1];
        bytes[0] = (byte) (b & 0xff);
        buffer += new String(bytes);

        if (buffer.endsWith("\n")) {
            buffer = buffer.substring(0, buffer.length () - 1);
            flush();
        }
    }

    /**
     * Flushes the output stream.
     *
     * @see OutputStream#flush()
     */
    public void flush () {
        switch(level) {
            case Trace:
                if (logger.isTraceEnabled()) {
                    logger.trace(buffer);
                }
                break;
            case Debug:
                if (logger.isDebugEnabled()) {
                    logger.debug(buffer);
                }
                break;
            case Info:
                if (logger.isInfoEnabled()) {
                    logger.info(buffer);
                }
                break;
            case Warn:
                if (logger.isWarnEnabled()) {
                    logger.warn(buffer);
                }
                break;
            case Error:
                if (logger.isErrorEnabled()) {
                    logger.error(buffer);
                }
                break;
            case Fatal:
                if (logger.isFatalEnabled()) {
                    logger.fatal(buffer);
                }
                break;
        }
        buffer = "";
    }
}
