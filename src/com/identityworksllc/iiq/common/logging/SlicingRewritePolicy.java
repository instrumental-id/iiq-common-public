package com.identityworksllc.iiq.common.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A log rewrite policy to extract bits out of an extremely long log message.
 * In IIQ, this might be web services output, a very large configuration
 * object, or other things.
 *
 * The goal is to mimic the 'grep' command with its -A and -B arguments. When
 * a part of the string matches the regex or the given static substring,
 * a certain number of characters before and after the matching segment will
 * be included in the log message. The remaining message will be dropped.
 *
 * Messages that don't match the regex or substring won't be included in the
 * log output at all.
 *
 * TODO finish this
 */
@Plugin(name = "SlicingRewritePolicy", category = "Core", elementType = "rewritePolicy", printObject = true)
public class SlicingRewritePolicy implements RewritePolicy {
    /**
     * The indexes of the slices of the message, which will be used to reconstruct
     * the string at the end of {@link #rewrite(LogEvent)}. Subsequent slices that
     * overlap will be merged into a single slice.
     */
    private static class StringSlice {
        /**
         * A comparator for sorting string slices by start index
         * @return The comparator
         */
        public static Comparator<StringSlice> comparator() {
            return Comparator.comparingInt(StringSlice::getStart);
        }

        /**
         * The end of the string segment
         */
        private final int end;

        /**
         * The length of the string being sliced. The 'end' is constrained
         * to be less than this value.
         */
        private final int maxLength;

        /**
         * The start of the string segment
         */
        private final int start;

        public StringSlice(int start, int end, int maxLength) {
            if (start < 0) {
                start = 0;
            } else if (end >= maxLength) {
                end = maxLength - 1;
            }

            this.start = start;
            this.end = end;
            this.maxLength = maxLength;
        }

        /**
         * Gets the start of the string slice
         * @return The start index of the string slice
         */
        public int getStart() {
            return start;
        }

        /**
         * Returns a new StringSlice that is the union of the two slices. The lowest
         * start and the highest end will be used.
         *
         * If the two slices do not overlap, a bunch of stuff between the end of the
         * earlier slice and the start of the later slice will be included.
         *
         * @param other The other slice to merge with
         * @return A new merged slice
         */
        public StringSlice mergeWith(StringSlice other) {
            return new StringSlice(Math.min(this.start, other.start), Math.max(this.end, other.end), maxLength);
        }

        /**
         * Returns true if this slice overlaps the other given slice.
         * @param other The other slice to check
         * @return True if the two slices overlap and can be merged
         */
        public boolean overlaps(StringSlice other) {
            return (this.start <= other.end) && (this.end >= other.start);
        }
    }

    public static class SlicingRewriteContextConfig {
        private final int contextChars;
        private final int endChars;
        private final int startChars;

        public SlicingRewriteContextConfig(int startChars, int endChars, int contextChars) {
            this.startChars = startChars;
            this.endChars = endChars;
            this.contextChars = contextChars;
        }
    }

    /**
     * The log4j2 factory method for creating one of these log event processors
     * @param regex The regex to use, if any
     * @param substring The substring to use, if any
     * @param startChars The number of characters to log from the start of the string
     * @param endChars The number of characters to log from the end of the string
     * @param contextChars The number of characters to print on either side of a regex or substring match
     * @return An instance of this rewrite policy
     */
    @PluginFactory
    public static SlicingRewritePolicy createSlicingPolicy(
            @PluginAttribute("regex") String regex,
            @PluginAttribute("substring") String substring,
            @PluginAttribute(value = "startChars", defaultInt = 50) int startChars,
            @PluginAttribute(value = "endChars", defaultInt = 50) int endChars,
            @PluginAttribute(value = "contextChars", defaultInt = 100) int contextChars) {

        SlicingRewriteContextConfig contextConfig = new SlicingRewriteContextConfig(startChars, endChars, contextChars);
        return new SlicingRewritePolicy(substring, regex, contextConfig);
    }

    private final SlicingRewriteContextConfig contextConfig;
    private final Pattern regexPattern;
    private final String substring;

    public SlicingRewritePolicy(String substring, String regex, SlicingRewriteContextConfig contextConfig) {
        this.substring = substring;

        if (regex != null && regex.length() > 0) {
            this.regexPattern = Pattern.compile(regex);
        } else {
            this.regexPattern = null;
        }

        this.contextConfig = contextConfig;
    }

    @Override
    public LogEvent rewrite(LogEvent source) {

        Message messageObject = source.getMessage();

        if (messageObject != null) {

            String message = messageObject.getFormattedMessage();
            if (message != null) {


                Log4jLogEvent.Builder eventBuilder = new Log4jLogEvent.Builder(source);
                return eventBuilder.build();
            }
        }


        return source;
    }

    /**
     * API method to extract slices from the given message string, based on the data
     * provided in the class configuration.
     *
     * If the message does not match either the substring or regex, the resulting Queue
     * will be empty and the message ought to be ignored for log purposes.
     *
     * @param message The message string
     * @return A linked list of string slices
     */
    public Queue<StringSlice> extractSlices(String message) {
        int size = message.length();
        boolean include = false;

        int firstIndex = -1;
        Matcher matcher = null;

        Queue<StringSlice> slices = new LinkedList<>();
        if (this.substring != null && this.substring.length() > 0) {
            firstIndex = message.indexOf(this.substring);
            include = (firstIndex >= 0);
        } else if (this.regexPattern != null) {
            matcher = this.regexPattern.matcher(message);
            include = matcher.find();
        }
        if (include) {

            if (firstIndex >= 0) {
                // Substring model
                StringSlice previousSlice = new StringSlice(0, this.contextConfig.startChars, size);

                int index = firstIndex;
                do {
                    StringSlice nextSlice = new StringSlice(index - this.contextConfig.contextChars, index + this.contextConfig.contextChars, size);
                    if (nextSlice.overlaps(previousSlice)) {
                        previousSlice = previousSlice.mergeWith(nextSlice);
                    } else {
                        slices.add(previousSlice);
                        previousSlice = nextSlice;
                    }

                    index = message.indexOf(this.substring, index + 1);
                } while(index >= 0);

                StringSlice endSlice = new StringSlice(size - this.contextConfig.endChars, size, size);

                if (previousSlice.overlaps(endSlice)) {
                    previousSlice = previousSlice.mergeWith(endSlice);
                    endSlice = null;
                }

                slices.add(previousSlice);

                if (endSlice != null) {
                    slices.add(endSlice);
                }
            } else {
                StringSlice previousSlice = new StringSlice(0, this.contextConfig.startChars, size);

                matcher.reset();

                while(matcher.find()) {
                    int startIndex = matcher.start();
                    int endIndex = matcher.end();
                    StringSlice nextSlice = new StringSlice(startIndex - this.contextConfig.contextChars, endIndex + this.contextConfig.contextChars, size);
                    if (nextSlice.overlaps(previousSlice)) {
                        previousSlice = previousSlice.mergeWith(nextSlice);
                    } else {
                        slices.add(previousSlice);
                        previousSlice = nextSlice;
                    }
                }

                StringSlice endSlice = new StringSlice(size - this.contextConfig.endChars, size, size);

                if (previousSlice.overlaps(endSlice)) {
                    previousSlice = previousSlice.mergeWith(endSlice);
                    endSlice = null;
                }

                slices.add(previousSlice);

                if (endSlice != null) {
                    slices.add(endSlice);
                }
            }

            // TODO append the slices

        }

        return slices;
    }
}
