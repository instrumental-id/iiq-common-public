package com.identityworksllc.iiq.common.minimal.logging;

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
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(name = "SlicingRewritePolicy", category = "Core", elementType = "rewritePolicy", printObject = true)
public class SlicingRewritePolicy implements RewritePolicy {
    private static class StringSlice {
        public static Comparator<StringSlice> comparator() {
            return Comparator.comparingInt(StringSlice::getStart);
        }

        private int end;
        private final int maxLength;
        private int start;

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

        public int getStart() {
            return start;
        }

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

    private static class SlicingRewriteContextConfig {
        private final int contextChars;
        private final int endChars;
        private final int startChars;

        public SlicingRewriteContextConfig(int startChars, int endChars, int contextChars) {
            this.startChars = startChars;
            this.endChars = endChars;
            this.contextChars = contextChars;
        }
    }

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
    private final String regexString;
    private final String substring;

    public SlicingRewritePolicy(String substring, String regex, SlicingRewriteContextConfig contextConfig) {
        this.substring = substring;
        this.regexString = regex;

        if (this.regexString != null && this.regexString.length() > 0) {
            this.regexPattern = Pattern.compile(this.regexString);
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
                boolean include = false;

                int firstIndex = -1;
                Matcher matcher = null;

                if (this.substring != null && this.substring.length() > 0) {
                    firstIndex = message.indexOf(this.substring);
                    include = (firstIndex >= 0);
                } else if (this.regexPattern != null) {
                    matcher = this.regexPattern.matcher(message);
                    include = matcher.find();
                }
                if (include) {
                    Log4jLogEvent.Builder eventBuilder = new Log4jLogEvent.Builder(source);

                    int size = message.length();

                    if (firstIndex >= 0) {
                        Queue<StringSlice> slices = new LinkedList<>();
                        StringSlice startSlice = new StringSlice(0, this.contextConfig.startChars, size);

                        int index = firstIndex;
                        do {
                            StringSlice nextSlice = new StringSlice(index - this.contextConfig.contextChars, index + this.contextConfig.contextChars, size);
                            if (nextSlice.overlaps(startSlice)) {
                                startSlice = startSlice.mergeWith(nextSlice);
                            }

                            index = message.indexOf(this.substring, index + 1);
                        } while(index >= 0);
                    } else {
                        // Matcher must be defined here, or else we would not have set include = true
                        // TODO
                    }

                    return eventBuilder.build();
                }
            }
        }


        return source;
    }
}
