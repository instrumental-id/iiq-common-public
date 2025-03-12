package com.identityworksllc.iiq.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjuster;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link TemporalAdjuster} that adds or subtracts a specified number of business days to a date.
 * A business day is a date that is not a weekend or in the list of holidays.
 *
 * For example:
 * {@code}
 *  LocalDate now = LocalDate.now();
 *  LocalDate nextBusinessDay = now.with(BusinessDateAdjuster.addWorkDays(1));
 * {@code}
 *
 * You can also use it with a {@link Calendar} object via the static helper methods.
 *
 * {@code}
 * Calendar now = Calendar.getInstance();
 * Calendar nextBusinessDay = BusinessDateAdjuster.adjustCalendar(now, 1);
 * {@code}
 */
public class BusinessDayAdjuster implements TemporalAdjuster {
    /**
     * The default set of weekend days (Saturday and Sunday).
     */
    public static final Set<DayOfWeek> DEFAULT_WEEKEND_DAYS = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    /**
     * The number of business days to add (positive) or subtract (negative).
     */
    private final int days;

    /**
     * The set of dates to be considered holidays (non-work days).
     */
    private final Set<LocalDate> holidays;

    /**
     * The set of days of the week to be considered part of the weekend.
     */
    private final Set<DayOfWeek> weekendDays;

    /**
     * Creates a new BusinessDayAdjuster with no holidays and the default set of weekend days.
     * @param days        The number of business days to add (positive) or subtract (negative)
     */
    public BusinessDayAdjuster(int days) {
        this(days, null, DEFAULT_WEEKEND_DAYS);
    }

    /**
     * Creates a new BusinessDayAdjuster with the given list of holidays and the default set of weekend days.
     * @param days        The number of business days to add (positive) or subtract (negative)
     * @param holidays    A set of dates to be considered holidays (non-work days)
     */
    public BusinessDayAdjuster(int days, Set<LocalDate> holidays) {
        this(days, holidays, DEFAULT_WEEKEND_DAYS);
    }

    /**
     * Creates a new BusinessDayAdjuster.
     *
     * @param days        The number of business days to add (positive) or subtract (negative)
     * @param holidays    A set of dates to be considered holidays (non-work days)
     * @param weekendDays A set of days to be considered weekends
     */
    public BusinessDayAdjuster(int days, Set<LocalDate> holidays, Set<DayOfWeek> weekendDays) {
        if (weekendDays != null && weekendDays.size() == 7) {
            throw new IllegalArgumentException("Weekend days cannot include all days of the week");
        }
        this.days = days;
        this.holidays = holidays != null ? holidays : new HashSet<>();
        this.weekendDays = weekendDays != null ? weekendDays : EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }

    /**
     * Creates a BusinessDayAdjuster that adds the specified number of work days.
     *
     * @param days        The number of work days to add
     * @return A BusinessDayAdjuster for use with {@link LocalDate#with(TemporalAdjuster)}
     */
    public static BusinessDayAdjuster addWorkDays(int days) {
        return new BusinessDayAdjuster(days, null, DEFAULT_WEEKEND_DAYS);
    }

    /**
     * Creates a BusinessDayAdjuster that adds the specified number of work days.
     *
     * @param days        The number of work days to add
     * @param holidays    A set of dates to be considered holidays
     * @return A BusinessDayAdjuster for use with {@link LocalDate#with(TemporalAdjuster)}
     */
    public static BusinessDayAdjuster addWorkDays(int days, Set<LocalDate> holidays) {
        return new BusinessDayAdjuster(days, holidays, DEFAULT_WEEKEND_DAYS);
    }

    /**
     * Creates a BusinessDayAdjuster that adds the specified number of work days.
     *
     * @param days        The number of work days to add
     * @param holidays    A set of dates to be considered holidays
     * @param weekendDays A set of days to be considered weekends
     * @return A BusinessDayAdjuster for use with {@link LocalDate#with(TemporalAdjuster)}
     */
    public static BusinessDayAdjuster addWorkDays(int days, Set<LocalDate> holidays, Set<DayOfWeek> weekendDays) {
        return new BusinessDayAdjuster(days, holidays, weekendDays);
    }

    /**
     * Adjusts the specified calendar by the required number of business days.
     * The input calendar is not modified.
     *
     * @param input The input calendar
     * @param days The number of business days to add (positive) or subtract (negative)
     * @return The adjusted calendar, a separate object from the original
     */
    public static Calendar adjustCalendar(Calendar input, int days) {
        return adjustCalendar(input, days, new HashSet<>());
    }

    /**
     * Adjusts the specified calendar by the required number of business days.
     * The input calendar is not modified.
     *
     * @param input The input calendar
     * @param days The number of business days to add (positive) or subtract (negative)
     * @param holidays A set of dates to be considered holidays
     * @return The adjusted calendar, a separate object from the original
     */
    public static Calendar adjustCalendar(Calendar input, int days, Set<LocalDate> holidays) {
        return adjustCalendar(input, days, holidays, DEFAULT_WEEKEND_DAYS);
    }

    /**
     * Adjusts the specified calendar by the required number of business days.
     * The input calendar is not modified.
     *
     * @param input The input calendar
     * @param days The number of business days to add (positive) or subtract (negative)
     * @param holidays A set of dates to be considered holidays
     * @param weekendDays A set of days to be considered weekends
     * @return The adjusted calendar, a separate object from the original
     */
    public static Calendar adjustCalendar(Calendar input, int days, Set<LocalDate> holidays, Set<DayOfWeek> weekendDays) {
        // Convert Calendar to LocalDate
        LocalDate date = LocalDate.of(input.get(Calendar.YEAR), input.get(Calendar.MONTH) + 1, input.get(Calendar.DAY_OF_MONTH));

        // Adjust the LocalDate
        LocalDate adjustedDate = date.with(BusinessDayAdjuster.addWorkDays(days, holidays, weekendDays));

        // Convert LocalDate back to Calendar
        Calendar output = Calendar.getInstance();
        output.set(adjustedDate.getYear(), adjustedDate.getMonthValue() - 1, adjustedDate.getDayOfMonth());

        // Restore the time component of the Calendar
        output.set(Calendar.HOUR_OF_DAY, input.get(Calendar.HOUR_OF_DAY));
        output.set(Calendar.MINUTE, input.get(Calendar.MINUTE));
        output.set(Calendar.SECOND, input.get(Calendar.SECOND));
        output.set(Calendar.MILLISECOND, input.get(Calendar.MILLISECOND));

        return output;
    }

    /**
     * Creates a BusinessDayAdjuster that subtracts the specified number of work days.
     *
     * @param days        The number of work days to add
     * @param holidays    A set of dates to be considered holidays
     * @return A BusinessDayAdjuster for use with {@link LocalDate#with(TemporalAdjuster)}
     */
    public static BusinessDayAdjuster subtractWorkDays(int days, Set<LocalDate> holidays) {
        return new BusinessDayAdjuster(-Math.abs(days), holidays, DEFAULT_WEEKEND_DAYS);
    }

    /**
     * Creates a BusinessDayAdjuster that subtracts the specified number of work days.
     *
     * @param days        The number of work days to add
     * @return A BusinessDayAdjuster for use with {@link LocalDate#with(TemporalAdjuster)}
     */
    public static BusinessDayAdjuster subtractWorkDays(int days) {
        return new BusinessDayAdjuster(-Math.abs(days), null, DEFAULT_WEEKEND_DAYS);
    }

    /**
     * Creates a WorkDayAdjuster that subtracts the specified number of work days.
     *
     * @param days        The number of work days to subtract
     * @param holidays    A set of dates to be considered holidays
     * @param weekendDays A set of days to be considered weekends
     * @return A BusinessDayAdjuster for use with {@link LocalDate#with(TemporalAdjuster)}
     */
    public static BusinessDayAdjuster subtractWorkDays(int days, Set<LocalDate> holidays, Set<DayOfWeek> weekendDays) {
        return new BusinessDayAdjuster(-Math.abs(days), holidays, weekendDays);
    }

    /**
     * Adjusts the specified temporal object by the required number of business days.
     *
     * @param temporal  the temporal object to adjust, not null
     * @return the adjusted temporal object, not null
     * @throws NullPointerException if the input object is null
     * @see TemporalAdjuster#adjustInto(Temporal)
     */
    @Override
    public Temporal adjustInto(Temporal temporal) {
        if (temporal == null) {
            throw new NullPointerException("temporal is null");
        }

        LocalDate date = LocalDate.from(temporal);

        // If no adjustment needed, return original date
        if (days == 0) {
            return date;
        }

        // Determine direction (adding or subtracting days)
        int direction = Integer.signum(days);
        int absWorkDays = Math.abs(days);

        int weekendDayCount = 7 - this.weekendDays.size();

        // Step 1: Calculate naive target date (ignoring holidays, including weekends)
        // For every {weekendDayCount} work days, we add 7 calendar days (i.e., a full week)
        int fullWeeks = absWorkDays / weekendDayCount;
        int remainingWorkDays = absWorkDays % weekendDayCount;

        // Calculate calendar days from full weeks (7 days per week)
        int calendarDays = fullWeeks * 7;

        // Add remaining work days, accounting for weekends
        LocalDate tempDate = date.plusDays(direction * calendarDays);
        int addedWorkDays = 0;

        while (addedWorkDays < remainingWorkDays) {
            tempDate = tempDate.plusDays(direction);
            DayOfWeek dayOfWeek = tempDate.getDayOfWeek();
            if (!weekendDays.contains(dayOfWeek)) {
                addedWorkDays++;
            }
        }

        while (weekendDays.contains(tempDate.getDayOfWeek())) {
            tempDate = tempDate.plusDays(direction);
        }

        // Step 2: Adjust for holidays
        // Get all holidays that fall on work days between start and end dates
        LocalDate startDate = direction > 0 ? date : tempDate;
        LocalDate endDate = direction > 0 ? tempDate : date;

        long holidaysInRange = holidays.stream()
                .filter(holiday -> !holiday.isBefore(startDate) && !holiday.isAfter(endDate))
                .filter(holiday -> {
                    DayOfWeek dow = holiday.getDayOfWeek();
                    return !weekendDays.contains(dow);
                })
                .count();

        // Add or subtract extra days to account for holidays
        if (holidaysInRange > 0) {
            for (int i = 0; i < holidaysInRange; i++) {
                // For each holiday, add days in the specified direction
                // until we find a non-holiday work day.
                while (true) {
                    tempDate = tempDate.plusDays(direction);
                    DayOfWeek dayOfWeek = tempDate.getDayOfWeek();

                    // Skip weekends
                    if (weekendDays.contains(dayOfWeek)) {
                        continue;
                    }

                    // Skip if this additional day is also a holiday
                    if (holidays.contains(tempDate)) {
                        continue;
                    }

                    break;
                }
            }
        }

        return tempDate;
    }
}