package com.identityworksllc.iiq.common.plugin.vo;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.TimeZone;

/**
 * A VO class to wrap a date in a known format, allowing clients to
 * consume it however they wish.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ExpandedDate {
    @JsonPropertyDescription("The date, rendered as an ISO8601 UTC timestamp string")
    private final String date;

    @JsonPropertyDescription("The server time zone ID, as returned by Java's ZoneId class")
    private final String serverTimeZoneId;
    @JsonPropertyDescription("The server's 'short' time zone name")
    private final String serverTimeZoneName;
    @JsonPropertyDescription("The timestamp in Unix epoch milliseconds")
    private final long timestamp;

    public ExpandedDate(Date in) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        this.timestamp = in.getTime();
        this.date = dateFormat.format(in);
        this.serverTimeZoneId = ZoneId.systemDefault().getId();
        this.serverTimeZoneName = ZoneId.systemDefault().getDisplayName(TextStyle.SHORT, Locale.US);
    }

    public String getDate() {
        return date;
    }

    public String getServerTimeZoneId() {
        return serverTimeZoneId;
    }

    public String getServerTimeZoneName() {
        return serverTimeZoneName;
    }

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
