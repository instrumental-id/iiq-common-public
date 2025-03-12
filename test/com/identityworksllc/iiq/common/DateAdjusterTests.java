package com.identityworksllc.iiq.common;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link BusinessDayAdjuster} class, with all kinds of
 * wacky edge cases.
 */
public class DateAdjusterTests {
    @Test
    public void adjustCalendarBackward() {
        Calendar input = Calendar.getInstance();
        input.set(2025, Calendar.MARCH, 6); // Thursday
        Calendar adjusted = BusinessDayAdjuster.adjustCalendar(input, -5);
        Calendar expected = Calendar.getInstance();
        expected.set(2025, Calendar.FEBRUARY, 27); // Previous Thursday
        assertEquals(expected.get(Calendar.YEAR), adjusted.get(Calendar.YEAR));
        assertEquals(expected.get(Calendar.MONTH), adjusted.get(Calendar.MONTH));
        assertEquals(expected.get(Calendar.DAY_OF_MONTH), adjusted.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void adjustCalendarBackwardAcrossYearBoundary() {
        Calendar input = Calendar.getInstance();
        input.set(2025, Calendar.JANUARY, 5); // Sunday
        Calendar adjusted = BusinessDayAdjuster.adjustCalendar(input, -5);
        Calendar expected = Calendar.getInstance();
        expected.set(2024, Calendar.DECEMBER, 27); // Previous Friday
        assertEquals(expected.get(Calendar.YEAR), adjusted.get(Calendar.YEAR));
        assertEquals(expected.get(Calendar.MONTH), adjusted.get(Calendar.MONTH));
        assertEquals(expected.get(Calendar.DAY_OF_MONTH), adjusted.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void adjustCalendarBackwardWithHolidays() {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(2025, 2, 28)); // Friday
        Calendar input = Calendar.getInstance();
        input.set(2025, Calendar.MARCH, 6); // Thursday
        Calendar adjusted = BusinessDayAdjuster.adjustCalendar(input, -5, holidays);
        Calendar expected = Calendar.getInstance();
        expected.set(2025, Calendar.FEBRUARY, 26); // Previous Wednesday
        assertEquals(expected.get(Calendar.YEAR), adjusted.get(Calendar.YEAR));
        assertEquals(expected.get(Calendar.MONTH), adjusted.get(Calendar.MONTH));
        assertEquals(expected.get(Calendar.DAY_OF_MONTH), adjusted.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void adjustCalendarForward() {
        Calendar input = Calendar.getInstance();
        input.set(2025, Calendar.MARCH, 6); // Thursday
        Calendar adjusted = BusinessDayAdjuster.adjustCalendar(input, 5);
        Calendar expected = Calendar.getInstance();
        expected.set(2025, Calendar.MARCH, 13); // Next Thursday
        assertEquals(expected.get(Calendar.YEAR), adjusted.get(Calendar.YEAR));
        assertEquals(expected.get(Calendar.MONTH), adjusted.get(Calendar.MONTH));
        assertEquals(expected.get(Calendar.DAY_OF_MONTH), adjusted.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void adjustCalendarForwardAcrossYearBoundary() {
        Calendar input = Calendar.getInstance();
        input.set(2024, Calendar.DECEMBER, 30); // Monday
        Calendar adjusted = BusinessDayAdjuster.adjustCalendar(input, 5);
        Calendar expected = Calendar.getInstance();
        expected.set(2025, Calendar.JANUARY, 6); // Next Monday
        assertEquals(expected.get(Calendar.YEAR), adjusted.get(Calendar.YEAR));
        assertEquals(expected.get(Calendar.MONTH), adjusted.get(Calendar.MONTH));
        assertEquals(expected.get(Calendar.DAY_OF_MONTH), adjusted.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void adjustCalendarForwardWithHolidays() {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(2025, 3, 10)); // Monday
        Calendar input = Calendar.getInstance();
        input.set(2025, Calendar.MARCH, 6); // Thursday
        Calendar adjusted = BusinessDayAdjuster.adjustCalendar(input, 5, holidays);
        Calendar expected = Calendar.getInstance();
        expected.set(2025, Calendar.MARCH, 14); // Next Friday
        assertEquals(expected.get(Calendar.YEAR), adjusted.get(Calendar.YEAR));
        assertEquals(expected.get(Calendar.MONTH), adjusted.get(Calendar.MONTH));
        assertEquals(expected.get(Calendar.DAY_OF_MONTH), adjusted.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void adjustDateBackward1() {
        LocalDate inputDate = LocalDate.of(2025, 3, 6);
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.subtractWorkDays(5));
        LocalDate expectedDate = LocalDate.of(2025, 2, 27);
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateBackward2() {
        LocalDate inputDate = LocalDate.of(2025, 3, 6);
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.subtractWorkDays(12));
        LocalDate expectedDate = LocalDate.of(2025, 2, 18);
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateBackwardOnWeekend() {
        // Start on a Sunday
        LocalDate inputDate = LocalDate.of(2025, 3, 9);
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.subtractWorkDays(1));
        LocalDate expectedDate = LocalDate.of(2025, 3, 7); // Previous Friday
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateBackwardWithConsecutiveHolidays() {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(2025, 2, 18));
        holidays.add(LocalDate.of(2025, 2, 19));

        LocalDate inputDate = LocalDate.of(2025, 3, 6);
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.subtractWorkDays(13, holidays));
        LocalDate expectedDate = LocalDate.of(2025, 2, 13);
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateBackwardWithHolidayOnWeekend() {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(2025, 3, 8)); // Saturday

        LocalDate inputDate = LocalDate.of(2025, 3, 9); // Sunday
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.subtractWorkDays(1, holidays));
        LocalDate expectedDate = LocalDate.of(2025, 3, 7); // Previous Friday
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateBackwardWithHolidays() {
        Set<LocalDate> holidays = new HashSet<>();
        // Without the holiday settings, this would otherwise be the end date, so it's a good edge test
        holidays.add(LocalDate.of(2025, 2, 18));


        LocalDate inputDate = LocalDate.of(2025, 3, 6);

        // 13 days with the holiday ends on a Sunday, so it should be the previous Friday
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.subtractWorkDays(13, holidays));
        LocalDate expectedDate = LocalDate.of(2025, 2, 14);
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateBackwardWithLeapYear() {
        LocalDate inputDate = LocalDate.of(2024, 3, 1); // Friday, leap year
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.subtractWorkDays(1));
        LocalDate expectedDate = LocalDate.of(2024, 2, 29); // Previous day, leap year
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateForward1() {
        LocalDate inputDate = LocalDate.of(2025, 3, 6);
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.addWorkDays(5));
        LocalDate expectedDate = LocalDate.of(2025, 3, 13);
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateForward2() {
        LocalDate inputDate = LocalDate.of(2025, 3, 6);
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.addWorkDays(12));
        LocalDate expectedDate = LocalDate.of(2025, 3, 24);
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateForward3() {
        // Start on a Saturday
        LocalDate inputDate = LocalDate.of(2025, 3, 8);

        // Next business day should be a Monday
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.addWorkDays(1));
        LocalDate expectedDate = LocalDate.of(2025, 3, 10);
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateForward4() {
        // Start on a Saturday
        LocalDate inputDate = LocalDate.of(2025, 3, 8);

        // Next business day should be a Monday
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.addWorkDays(7));
        LocalDate expectedDate = LocalDate.of(2025, 3, 18);
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateForwardOnHoliday() {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(2025, 3, 10)); // Monday

        // Start on a Friday
        LocalDate inputDate = LocalDate.of(2025, 3, 7);
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.addWorkDays(1, holidays));
        LocalDate expectedDate = LocalDate.of(2025, 3, 11); // Skip Monday holiday
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateForwardWithAllDaysAsWeekend() {
        Set<DayOfWeek> weekendDays = EnumSet.allOf(DayOfWeek.class);
        LocalDate inputDate = LocalDate.of(2025, 3, 3);
        assertThrows(IllegalArgumentException.class, () -> inputDate.with(BusinessDayAdjuster.addWorkDays(1, null, weekendDays)));
    }

    @Test
    public void adjustDateForwardWithAlternateWeekendDays() {
        Set<DayOfWeek> weekendDays = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.FRIDAY);
        // Start on a Thursday
        LocalDate inputDate = LocalDate.of(2025, 3, 6);

        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.addWorkDays(7, null, weekendDays));
        LocalDate expectedDate = LocalDate.of(2025, 3, 19);
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateForwardWithAlternateWeekendDays2() {
        Set<DayOfWeek> weekendDays = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.THURSDAY);
        // Start on a Thursday
        LocalDate inputDate = LocalDate.of(2025, 3, 6);

        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.addWorkDays(36, null, weekendDays));
        LocalDate expectedDate = LocalDate.of(2025, 5, 9);
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateForwardWithAlternateWeekendDaysAndHolidays() {
        Set<DayOfWeek> weekendDays = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.FRIDAY);
        Set<LocalDate> holidays = new HashSet<>();
        // Without the holiday settings, this would otherwise be the end date, so it's a good edge test
        holidays.add(LocalDate.of(2025, 3, 10));


        // Start on a Thursday
        LocalDate inputDate = LocalDate.of(2025, 3, 6);

        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.addWorkDays(7, holidays, weekendDays));
        LocalDate expectedDate = LocalDate.of(2025, 3, 20);
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateForwardWithHolidayOnWeekend() {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(2025, 3, 8)); // Saturday

        LocalDate inputDate = LocalDate.of(2025, 3, 7); // Friday
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.addWorkDays(1, holidays));
        LocalDate expectedDate = LocalDate.of(2025, 3, 10); // Next Monday
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateForwardWithHolidays() {
        Set<LocalDate> holidays = new HashSet<>();
        // Without the holiday settings, this would otherwise be the end date, so it's a good edge test
        holidays.add(LocalDate.of(2025, 3, 24));

        LocalDate inputDate = LocalDate.of(2025, 3, 6);
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.addWorkDays(12, holidays));
        LocalDate expectedDate = LocalDate.of(2025, 3, 25);
        assertEquals(expectedDate, adjustedDate);
    }

    @Test
    public void adjustDateForwardWithWeekendAndHoliday() {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(2025, 3, 10)); // Monday

        // Start on a Friday
        LocalDate inputDate = LocalDate.of(2025, 3, 7);
        LocalDate adjustedDate = inputDate.with(BusinessDayAdjuster.addWorkDays(2, holidays));
        LocalDate expectedDate = LocalDate.of(2025, 3, 12); // Skip weekend and Monday holiday
        assertEquals(expectedDate, adjustedDate);
    }
}
