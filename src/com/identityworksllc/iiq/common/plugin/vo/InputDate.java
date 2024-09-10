package com.identityworksllc.iiq.common.plugin.vo;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

/**
 * The JAX-RS spec requires that an input bean for {@link javax.ws.rs.QueryParam}
 * or {@link javax.ws.rs.PathParam} have either a String constructor or a static
 * valueOf(String) method. This is that bean, for dates.
 *
 * The string input must either the ISO8601 local date, offset date time, or instant
 * formats. Other formats will not be interpreted successfully.
 *
 * The stored value will be a {@link ZonedDateTime} by default.
 *
 * @see <a href="https://docs.oracle.com/javaee/7/api/javax/ws/rs/QueryParam.html">QueryParam</a>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class InputDate {

    /**
     * The regex to recognize an ISO8601 date (yyyy-mm-dd)
     */
    private static final String ISO8601_DATE_REGEX = "^\\d{4}-\\d\\d-\\d\\d$";

    /**
     * The regex to recognize an ISO8601 date-time, with "T" in the middle
     */
    private static final String ISO8601_FULL_REGEX = "^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d(.\\d+)?(([+-]\\d\\d:\\d\\d)|Z)?$";

    /**
     * The logger
     */
    private static final Log logger = LogFactory.getLog(InputDate.class);

    /**
     * Translates the String to an InputDate. This is the method required by JAX-RS.
     *
     * @param input The input string, in either epoch milliseconds or ISO8601 format
     * @return The InputDate
     */
    public static InputDate valueOf(String input) {
        if (logger.isDebugEnabled()) {
            logger.debug("Parsing input date: " + input);
        }

        InputDate result;

        if (input.matches("^\\d+$")) {
            // Epoch timestamp
            long timestamp = Long.parseLong(input);
            result = new InputDate(timestamp);
        } else if (input.matches(ISO8601_FULL_REGEX)) {
            TemporalAccessor ta = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(input);
            result = new InputDate(ZonedDateTime.from(ta));
        } else if (input.matches(ISO8601_DATE_REGEX)) {
            TemporalAccessor ta = DateTimeFormatter.ISO_DATE.parse(input);
            LocalDate ld = LocalDate.from(ta);
            result = new InputDate(ld.atStartOfDay(ZoneId.systemDefault()));
        } else {
            throw new IllegalArgumentException("Unrecognized date format (expected epoch milliseconds, ISO8601 date (yyyy-mm-dd), or ISO8601 date-time): " + input);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Parsed input date: " + result.date);
        }

        return result;
    }

    /**
     * The stored date-time object
     */
    private final ZonedDateTime date;

    /**
     * Constructs a new InputDate from a long value
     * @param input an epoch millisecond timestamp
     */
    public InputDate(long input) {
        this.date = Instant.ofEpochMilli(input).atZone(ZoneId.systemDefault());
    }

    /**
     * Constructs a new InputDate from an instant
     * @param from An instant
     */
    public InputDate(Instant from) {
        this.date = from.atZone(ZoneId.systemDefault());
    }

    /**
     * Constructs a new InputDate from a {@link Date}
     * @param input The input date
     */
    public InputDate(Date input) {
        this.date = ZonedDateTime.ofInstant(input.toInstant(), ZoneId.systemDefault());
    }

    /**
     * Constructs a new InputDate from a ZonedDateTime. The value is
     * stored directly in this class.
     *
     * @param zdt The {@link ZonedDateTime} input
     */
    public InputDate(ZonedDateTime zdt) {
        this.date = zdt;
    }

    /**
     * Returns the epoch millisecond rendition of the date
     *
     * @return The date in epoch milliseconds
     */
    public long toEpochMillis() {
        return date.toInstant().toEpochMilli();
    }

    /**
     * Returns the java.util.Date object
     *
     * @return The java date object
     */
    public Date toJavaData() {
        return Date.from(date.toInstant());
    }

    @Override
    public String toString() {
        return DateTimeFormatter.ISO_DATE_TIME.format(this.date);
    }

    /**
     * Returns the input date as a ZonedDateTime
     *
     * @return The ZonedDateTime
     */
    public ZonedDateTime toZonedDateTime() {
        return date;
    }
}
