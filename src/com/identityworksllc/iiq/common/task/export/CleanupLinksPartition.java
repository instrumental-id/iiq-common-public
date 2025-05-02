package com.identityworksllc.iiq.common.task.export;

import com.identityworksllc.iiq.common.query.NamedParameterStatement;
import org.apache.commons.logging.Log;
import sailpoint.api.SailPointContext;
import sailpoint.server.Environment;
import sailpoint.tools.GeneralException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class CleanupLinksPartition extends ExportPartition {

    protected static final String SQL_GET_EXISTING_LINKS = "select id from spt_link";
    protected static final String SQL_GET_MAPPED_LINKS = "select id from de_link";

    @Override
    public void export(SailPointContext context, Connection connection, Log logger) throws GeneralException {
        Set<String> exportIds = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_GET_MAPPED_LINKS)) {
            try (ResultSet results = statement.executeQuery()) {
                while(results.next()) {
                    exportIds.add(results.getString(1));
                }
            }
        } catch(SQLException e) {
            throw new GeneralException(e);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Found " + exportIds.size() + " exported Links in the export table");
        }

        try (Connection localConnection = Environment.getEnvironment().getSpringDataSource().getConnection()) {
            try (PreparedStatement statement = localConnection.prepareStatement(SQL_GET_EXISTING_LINKS)) {
                try (ResultSet results = statement.executeQuery()) {
                    while(results.next()) {
                        exportIds.remove(results.getString(1));
                    }
                }
            }
        } catch(SQLException e) {
            throw new GeneralException(e);
        }

        // After this, what's left should be rows in the de_link table that are not in IIQ

        try (NamedParameterStatement deleteLink = new NamedParameterStatement(connection, ExportLinksPartition.DELETE_LINK); NamedParameterStatement deleteAttrs = new NamedParameterStatement(connection, ExportLinksPartition.DELETE_LINK_ATTRS)) {
            for (String id : exportIds) {
                deleteLink.setString("id", id);
                deleteAttrs.setString("id", id);

                deleteAttrs.executeUpdate();
                deleteLink.executeUpdate();
            }
        } catch(SQLException e) {
            throw new GeneralException(e);
        }
    }
}
