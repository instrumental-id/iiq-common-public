package com.identityworksllc.iiq.common.minimal.logging;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * An abstract log listener that will receive log events instead of queueing them
 */
@FunctionalInterface
public interface LogListener {
	
	/**
	 * A log message emitted if a listener is assigned to the log capture engine.
	 * 
	 * TODO make this more Beanshell friendly (map?)
	 */
	@SuppressWarnings("javadoc")
	final class LogMessage implements Serializable {
				
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		
		private final Date date;
		private final String level;
		private final String message;
		private final String source;
		private final String[] throwableLines;
		
		public LogMessage(Date date, String level, String source, String message, String[] throwableLines) {
			if (date == null) {
				throw new NullPointerException("date");
			}
			if (message == null) {
				throw new NullPointerException("message");
			}
			
			this.date = date;
			this.message = message;

			if (level == null) {
				this.level = "";
			} else {
				this.level = level;
			}
			
			if (source == null) {
				this.source = "";
			} else {
				this.source = source;
			}

			if (throwableLines != null) {
				this.throwableLines = throwableLines;
			} else {
				this.throwableLines = new String[0];
			}
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof LogMessage)) return false;
			LogMessage that = (LogMessage) o;
			return Objects.equals(date, that.date) &&
					Objects.equals(level, that.level) &&
					Objects.equals(source, that.source) &&
					Objects.equals(message, that.message) &&
					Arrays.equals(throwableLines, that.throwableLines);
		}

		/**
		 * Retrieves the date of
		 * @return
		 */
		public final Date getDate() {
			return date;
		}

		public final String getLevel() {
			return level;
		}

		public final String getMessage() {
			return message;
		}

		public final String getSource() {
			return source;
		}

		public final String[] getThrowableLines() {
			return this.throwableLines;
		}

		public final boolean hasThrowable() {
			return this.throwableLines.length > 0;
		}

		@Override
		public int hashCode() {
			return Objects.hash(date, level, source, message, throwableLines);
		}

		@Override
		public String toString() {
			return new StringJoiner(", ", LogMessage.class.getSimpleName() + "[", "]")
					.add("date=" + date)
					.add("level='" + level + "'")
					.add("source='" + source + "'")
					.add("message='" + message + "'")
					.add("error?='" + hasThrowable() + "'")
					.toString();
		}
	}
	
	/**
	 * A callback invoked on this listener when a log message is received in the listener's thread.
	 * @param message The message to log. All fields are guaranteed to not be null.
	 */
	void logMessageReceived(LogMessage message);
}
