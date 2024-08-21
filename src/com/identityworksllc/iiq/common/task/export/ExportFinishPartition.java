package com.identityworksllc.iiq.common.task.export;

import org.apache.commons.logging.Log;
import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Finishes the export by removing the previous run record and adding the new one, with
 * the latest export date
 */
public class ExportFinishPartition extends ExportPartition {
    private String configHash;

    @Override
    protected void export(SailPointContext context, Connection connection, Log logger) throws GeneralException {
        try (PreparedStatement delete = connection.prepareStatement("delete from de_runs"); PreparedStatement insert = connection.prepareStatement("insert into de_runs ( last_start_time, config_hash ) values ( ? )")) {
            delete.executeUpdate();
            insert.setLong(1, exportTimestamp);
            insert.setString(2, configHash);
            insert.executeUpdate();

            connection.commit();
        } catch(SQLException e) {
            throw new GeneralException(e);
        }
    }

    /**
     * Sets the config hash
     * @param configHash
     */
    public void setConfigHash(String configHash) {
        this.configHash = configHash;
    }
}
