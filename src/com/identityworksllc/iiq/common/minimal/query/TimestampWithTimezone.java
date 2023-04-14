package com.identityworksllc.iiq.common.minimal.query;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;

/**
 * A small wrapper class for use with "Parameters" for setting a
 * timestamp with timezone in a bulk context.
 */
public class TimestampWithTimezone extends java.sql.Timestamp {
    /**
     * The timezone associated with this timestamp
     */
    private final ZoneId timezone;

    /**
     * Creates a new instance from a Calendar, which must have a non-null time zone
     * @param time The calendar instance
     */
    public TimestampWithTimezone(Calendar time) {
        this(Objects.requireNonNull(time).getTimeInMillis(), Objects.requireNonNull(time.getTimeZone()).toZoneId());
    }

    /**
     * Creates a new instance from a ZonedDateTime
     * @param time The ZonedDateTime instance
     */
    public TimestampWithTimezone(ZonedDateTime time) {
        this(Objects.requireNonNull(time).toInstant().toEpochMilli(), time.getZone());
    }

    /**
     * Creates a new instance from an epoch millisecond timestamp and a TimeZone
     * @param time The epoch millisecond timestamp
     * @param tz The time zone
     */
    public TimestampWithTimezone(long time, TimeZone tz) {
        this(time, Objects.requireNonNull(tz).toZoneId());
    }

    /**
     * Creates a new instance from an epoch millisecond timestamp and a ZoneId
     * @param time The epoch millisecond timestamp
     * @param tz The zone ID
     */
    public TimestampWithTimezone(long time, ZoneId tz) {
        super(time);
        this.timezone = Objects.requireNonNull(tz);
    }

    /**
     * Gets the ZoneId associated with this timestamp
     * @return The zone ID
     */
    public ZoneId getZone() {
        return timezone;
    }

    /**
     * Gets the zoned calendar for use with PreparedStatement.setTimestamp and other methods
     * @return The zoned calendar object
     */
    public Calendar getZonedCalendar() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(this.getTime());
        cal.setTimeZone(TimeZone.getTimeZone(timezone));
        return cal;
    }
}
