package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.plugin.CommonPluginUtils;
import com.identityworksllc.iiq.common.service.BaseCommonService;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.tools.GeneralException;

import java.util.Iterator;
import java.util.Map;

public class RoleTargetCleanupService extends AbstractThreadedTask<String> {

    @Override
    protected Iterator<? extends String> getObjectIterator(SailPointContext context, Attributes<String, Object> args) throws GeneralException {

        return null;
    }

    @Override
    public Object threadExecute(SailPointContext threadContext, Map<String, Object> parameters, String obj) throws GeneralException {
        return null;
    }
}
