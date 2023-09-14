package com.identityworksllc.iiq.common;

import com.identityworksllc.iiq.common.plugin.vo.InputDate;
import org.junit.jupiter.api.Test;

/**
 * Sanity check tests for the {@link InputDate} class
 */
public class TestInputDateVO {

    private void parseAndPrint(String input) {
        InputDate date = InputDate.valueOf(input);

        System.out.println(date);
    }

    @Test
    public void testEpochDate() {
        parseAndPrint("1694699768000");
    }

    @Test
    public void testISODate() {
        parseAndPrint("2020-04-01");
    }

    @Test
    public void testISODateTime() {
        parseAndPrint("2012-10-06T04:13:00+00:00");
    }
}
