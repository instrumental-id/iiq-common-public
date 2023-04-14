package com.identityworksllc.iiq.common.minimal.table;

import sailpoint.tools.GeneralException;

/**
 * A cell modifier that can be passed in to various methods
 */
@FunctionalInterface
public interface CellOption {
    /**
     * Modify the cell passed in according to the option details
     * @param cell The cell to modify
     * @throws GeneralException on any failures
     */
    void accept(Cell cell) throws GeneralException;
}
