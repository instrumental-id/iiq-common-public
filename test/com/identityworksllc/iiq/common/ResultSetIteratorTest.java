package com.identityworksllc.iiq.common;

import com.identityworksllc.iiq.common.iterators.ResultSetIterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import sailpoint.api.SailPointContext;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ResultSetIteratorTest {

    private SailPointContext mockContext;
    private ResultSetMetaData mockMetaData;
    private ResultSet mockResultSet;

    @Test
    void close_closesResultSet() throws SQLException {
        ResultSetIterator iterator = new ResultSetIterator(mockResultSet, mockContext);
        iterator.close();

        verify(mockResultSet, times(1)).close();
    }

    @Test
    void constructor_withEmptyColumns_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new ResultSetIterator(mockResultSet, Arrays.asList(), mockContext));
    }

    @Test
    void constructor_withNullResultSet_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new ResultSetIterator(null, mockContext));
    }

    @Test
    void hasNext_withEmptyResultSet_returnsFalse() throws SQLException {
        when(mockResultSet.next()).thenReturn(false);

        ResultSetIterator iterator = new ResultSetIterator(mockResultSet, mockContext);

        assertFalse(iterator.hasNext());
    }

    @Test
    void hasNext_withNonEmptyResultSet_returnsTrue() throws SQLException {
        when(mockResultSet.next()).thenReturn(true);

        ResultSetIterator iterator = new ResultSetIterator(mockResultSet, mockContext);

        assertTrue(iterator.hasNext());
    }

    @Test
    void nextRow_returnsRowAsMap() throws SQLException {
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("column1")).thenReturn("value1");
        when(mockResultSet.getLong("column2")).thenReturn(123L);

        ResultSetIterator iterator = new ResultSetIterator(mockResultSet, Arrays.asList("column1", "column2"), mockContext);

        assertTrue(iterator.hasNext());
        assertEquals("value1", iterator.nextRow().get("column1"));
        assertEquals(123L, iterator.nextRow().get("column2"));
    }

    @Test
    void next_withNoMoreRows_throwsNoSuchElementException() throws SQLException {
        when(mockResultSet.next()).thenReturn(false);

        ResultSetIterator iterator = new ResultSetIterator(mockResultSet, mockContext);

        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void next_withValidResultSet_returnsRowData() throws SQLException {
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("column1")).thenReturn("value1");
        when(mockResultSet.getLong("column2")).thenReturn(123L);

        ResultSetIterator iterator = new ResultSetIterator(mockResultSet, mockContext);

        assertTrue(iterator.hasNext());
        Object[] row = iterator.next();
        assertEquals("value1", row[0]);
        assertEquals(123L, row[1]);
    }

    @BeforeEach
    void setUp() throws SQLException {
        mockResultSet = mock(ResultSet.class);
        mockContext = mock(SailPointContext.class);
        mockMetaData = mock(ResultSetMetaData.class);

        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(2);
        when(mockMetaData.getColumnLabel(1)).thenReturn("column1");
        when(mockMetaData.getColumnLabel(2)).thenReturn("column2");
        when(mockMetaData.getColumnType(1)).thenReturn(java.sql.Types.VARCHAR);
        when(mockMetaData.getColumnType(2)).thenReturn(java.sql.Types.INTEGER);
    }
}