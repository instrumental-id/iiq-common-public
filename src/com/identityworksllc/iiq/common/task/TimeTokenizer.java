package com.identityworksllc.iiq.common.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes time tokens in a particular string, typically for task scheduling.
 *
 * Time tokens can be any of:
 *
 *  - $NOW                      The current timestamp
 *  - $MIDNIGHT                 The most recent midnight in the time zone given
 *  - $YESTERDAY                The second most recent midnight in the time zone given
 *  - $LASTSTART                The last time the task was started, or 0 if no data
 *  - $LASTSTOP                 The last time the task was stopped, or 0 if no data
 *  - $(anyVariable as format)
 *       Formats the input timestamp using the given format (for querying string dates)
 *
 *  - $(today at 14:00)
 *  - $(yesterday at 10:00)
 *       The described timestamp
 *
 *  - $(+/-N UNITS)
 *       An offset of N UNITS (e.g. "-3 hours"), where the unit is one of {@link ChronoUnit}
 *
 *  - $(+/-N UNITS at midnight)
 *  - $(LASTSTOP -N UNITS)
 */
public class TimeTokenizer {

    private static final Log log = LogFactory.getLog(TimeTokenizer.class);

    private static final Pattern tokenFormatPattern = Pattern.compile("\\$\\(([^:]*?) as (.*?)\\)");

    /**
     * Parses the input string, which may or may not contain time tokens, by
     * replacing the token with a string suitable for a Sailpoint filter (i.e.,
     * the DATE$ format).
     *
     * @param taskSchedule The task schedule, optionally. It will be used to populate the LASTSTART and LASTSTOP tokens if present.
     * @param inputString the input string to replace tokens within
     * @param zoneIdString The timezone to use for time tokens (optional)
     * @return The parsed and reformatted input string
     * @throws GeneralException if any failures occur during parsing
     */
    public static String parseTimeComponents(TaskSchedule taskSchedule, String inputString, String zoneIdString) throws GeneralException {
        log.debug("String before token replacement: " + inputString);

        ZoneId zoneId = ZoneId.systemDefault();
        if (Util.isNotNullOrEmpty(zoneIdString)) {
            zoneId = ZoneId.of(zoneIdString);
        }
        Instant now = Instant.now();
        Instant midnight = LocalDate.now().atTime(LocalTime.MIDNIGHT).atZone(zoneId).toInstant();
        Instant yesterday = LocalDate.now().atTime(LocalTime.MIDNIGHT).minus(1, ChronoUnit.DAYS).atZone(zoneId).toInstant();

        Map<String, Long> tokenMap = new HashMap<>();
        tokenMap.put("NOW", now.toEpochMilli());
        tokenMap.put("MIDNIGHT", midnight.toEpochMilli());
        tokenMap.put("YESTERDAY", yesterday.toEpochMilli());

        if (taskSchedule != null) {
            tokenMap.put("LASTSTART", 0L);
            tokenMap.put("LASTSTOP", 0L);

            if (taskSchedule.getLastExecution() != null) {
                tokenMap.put("LASTSTART", taskSchedule.getLastExecution().getTime());
            }

            if (taskSchedule.getLatestResult() != null) {
                TaskResult latestResult = taskSchedule.getLatestResult();
                if (latestResult.getCompleted() != null) {
                    tokenMap.put("LASTSTOP", latestResult.getCompleted().getTime());
                }
            }
        }

        for(Map.Entry<String, Long> token : tokenMap.entrySet()) {
            inputString = inputString.replace("$" + token.getKey(), "DATE$" + token.getValue());
            inputString = inputString.replace("$" + token.getKey().toLowerCase(Locale.ROOT), "DATE$" + token.getValue());
        }

        StringBuffer replacement = new StringBuffer();
        Matcher matcher = tokenFormatPattern.matcher(inputString);
        while(matcher.find()) {
            String token = matcher.group(1);
            String formatString = matcher.group(2);
            Long timestamp = tokenMap.get(token.toUpperCase(Locale.ROOT));
            if (timestamp == null) {
                matcher.appendReplacement(replacement, Matcher.quoteReplacement(matcher.group()));
            } else {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(formatString);
                String formattedDate = simpleDateFormat.format(new Date(timestamp));
                matcher.appendReplacement(replacement, Matcher.quoteReplacement(formattedDate));
            }
        }
        matcher.appendTail(replacement);

        inputString = replacement.toString();

        String outputString = replaceOffsetTokens(inputString, tokenMap, now, midnight);
        outputString = replaceTimeTokens(outputString, zoneId);

        log.debug("String after token replacement: " + outputString);
        return outputString;
    }

    /**
     * Replaces any offset-type tokens, like $(-2 hours), within the string. The
     * unit should be a ChronoUnit.
     */
    public static String replaceOffsetTokens(String inputString, Map<String, Long> tokenMap, Instant now, Instant midnight) {
        Pattern subtractPattern = Pattern.compile("\\$\\(" +
                "(LASTSTART |LASTSTOP )?([\\-+]?\\d+) (\\w+?)( at midnight)?( as (.*?))?" +
                "\\)");
        StringBuffer finalFilter = new StringBuffer();
        Matcher matcher = subtractPattern.matcher(inputString);
        while(matcher.find()) {
            String offset = matcher.group(1);
            String amount = matcher.group(2);
            String type = matcher.group(3);
            String atMidnight = matcher.group(4);
            String formatString = matcher.group(6);
            ChronoUnit unit = ChronoUnit.valueOf(type.toUpperCase());
            int amountNum = Util.otoi(amount);

            Instant start;
            if (Util.isNotNullOrEmpty(offset)) {
                String which = offset.trim().toUpperCase();
                Long startingPointOffset = tokenMap.get(which);
                if (startingPointOffset == null) {
                    startingPointOffset = 0L;
                }
                if (startingPointOffset == 0) {
                    log.warn("Starting point for date offset " + which + " is zero");
                }
                start = Instant.ofEpochMilli(startingPointOffset);
            } else {
                start = (Util.isNullOrEmpty(atMidnight) ? now : midnight);
            }

            Instant when = start.plus(amountNum, unit);
            if (Util.isNotNullOrEmpty(formatString)) {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(formatString);
                String replacement = simpleDateFormat.format(new Date(when.toEpochMilli()));
                matcher.appendReplacement(finalFilter, Matcher.quoteReplacement(replacement));
            } else {
                String replacement = "DATE$" + when.toEpochMilli();
                matcher.appendReplacement(finalFilter, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(finalFilter);
        return finalFilter.toString();
    }

    /**
     * Replaces any time-type tokens $(today at 14:00) in the input string
     */
    public static String replaceTimeTokens(String inputString, ZoneId timeZone) {
        Pattern subtractPattern = Pattern.compile("\\$\\(" +
                "(today|tomorrow|yesterday) at (\\d+:\\d+(:\\d+)?)( as (.*?))?" +
                "\\)");
        StringBuffer finalFilter = new StringBuffer();
        Matcher matcher = subtractPattern.matcher(inputString);
        while(matcher.find()) {
            String type = matcher.group(1);
            String timeString = matcher.group(2);
            String seconds = matcher.group(3);
            String format = matcher.group(5);

            // Append seconds if needed or it won't parse
            if (Util.isNullOrEmpty(seconds)) {
                timeString += ":00";
            }
            // Prepend a leading zero to the hour if needed or it won't parse
            if (timeString.indexOf(":") == 1) {
                timeString = "0" + timeString;
            }

            LocalTime timeOffset = LocalTime.parse(timeString);

            LocalDateTime dateTimeOffset = LocalDate.now().atTime(timeOffset);

            if (Util.nullSafeEq(type, "yesterday")) {
                dateTimeOffset = dateTimeOffset.minus(1, ChronoUnit.DAYS);
            } else if (Util.nullSafeEq(type, "tomorrow")) {
                dateTimeOffset = dateTimeOffset.plus(1, ChronoUnit.DAYS);
            }

            ZonedDateTime instant = dateTimeOffset.atZone(timeZone);

            if (Util.isNotNullOrEmpty(format)) {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
                String replacement = simpleDateFormat.format(new Date(instant.toInstant().toEpochMilli()));
                matcher.appendReplacement(finalFilter, Matcher.quoteReplacement(replacement));
            } else {
                String replacement = "DATE$" + instant.toInstant().toEpochMilli();
                matcher.appendReplacement(finalFilter, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(finalFilter);
        return finalFilter.toString();
    }
}
