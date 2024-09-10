package com.identityworksllc.iiq.common.plugin.vo;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.TimeZone;

/**
 * A VO class to wrap a date in a known format, allowing clients to
 * consume it however they wish.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ExpandedDate {
    @JsonPropertyDescription("The date, rendered as an ISO8601 UTC date or date-time string")
    private final String date;

    @JsonPropertyDescription("The server time zone ID, as returned by Java's ZoneId class")
    private final String serverTimeZoneId;
    @JsonPropertyDescription("The server's 'short' time zone name")
    private final String serverTimeZoneName;
    @JsonPropertyDescription("The timestamp in Unix epoch milliseconds")
    private final long timestamp;

    /**
     * Constructs a new ExpandedDate from the given LocalDate. In this case, the formatted
     * date will be in the ISO8601 local date standard of yyyy-MM-dd. The timestamp will
     * be set to local midnight in the system time zone.
     *
     * @param in The date to convert
     */
    public ExpandedDate(LocalDate in) {
        Objects.requireNonNull(in, "Cannot expand a null Date");

        DateTimeFormatter dateFormat = DateTimeFormatter.ISO_LOCAL_DATE;

        this.timestamp = in.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        this.date = in.format(dateFormat);
        this.serverTimeZoneId = ZoneId.systemDefault().getId();
        this.serverTimeZoneName = ZoneId.systemDefault().getDisplayName(TextStyle.SHORT, Locale.US);
    }

    /**
     * Constructs a new ExpandedDate by converting the given ZonedDateTime to an epoch instant.
     * @param date  The date to convert
     */
    public ExpandedDate(ZonedDateTime date) {
        this(Date.from(date.toInstant()));
    }

    /**
     * Constructs a new ExpandedDate by converting the given LocalDateTime to an epoch instant
     * in the system time zone.
     *
     * @param date The local date time
     */
    public ExpandedDate(LocalDateTime date) {
        this(Date.from(date.atZone(ZoneId.systemDefault()).toInstant()));
    }

    /**
     * Constructs a new ExpandedDate by treating the Instant as a Date with millisecond
     * precision. This is technically a loss of precision, as Instant values are more
     * precise than Date values.
     *
     * @param instant The instant to convert to an ExpandedDate
     */
    public ExpandedDate(Instant instant) {
        this(Date.from(instant));
    }

    /**
     * Constructs a new ExpandedDate from the given input date
     * @param in The input date, not null
     */
    public ExpandedDate(Date in) {
        Objects.requireNonNull(in, "Cannot expand a null Date");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        this.timestamp = in.getTime();
        this.date = dateFormat.format(in);
        this.serverTimeZoneId = ZoneId.systemDefault().getId();
        this.serverTimeZoneName = ZoneId.systemDefault().getDisplayName(TextStyle.SHORT, Locale.US);
    }

    /**
     * @return the input date as an ISO8601 timestamp
     */
    public String getDate() {
        return date;
    }

    /**
     * @return the server time zone ID according to {@link ZoneId#getId()}
     */
    public String getServerTimeZoneId() {
        return serverTimeZoneId;
    }

    /**
     * @return the server time zone ID according to {@link ZoneId#getDisplayName(TextStyle, Locale)}}, with a text style of {@link TextStyle#SHORT} and a locale of {@link Locale#US}.
     */
    public String getServerTimeZoneName() {
        return serverTimeZoneName;
    }

    /**
     * @return the timestamp as a millisecond Unix epoch offset
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", ExpandedDate.class.getSimpleName() + "[", "]");
        if ((date) != null) {
            joiner.add("date='" + date + "'");
        }
        if ((serverTimeZoneId) != null) {
            joiner.add("serverTimeZoneId='" + serverTimeZoneId + "'");
        }
        if ((serverTimeZoneName) != null) {
            joiner.add("serverTimeZoneName='" + serverTimeZoneName + "'");
        }
        joiner.add("timestamp=" + timestamp);
        return joiner.toString();
    }
}
