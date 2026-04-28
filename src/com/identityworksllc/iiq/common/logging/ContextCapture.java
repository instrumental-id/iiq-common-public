package com.identityworksllc.iiq.common.logging;

import com.identityworksllc.iiq.common.annotation.Experimental;
import org.apache.logging.log4j.ThreadContext;
import sailpoint.object.SailPointObject;
import sailpoint.object.Workflow;
import sailpoint.workflow.WorkflowContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An in-progress utility for capturing and restoring ThreadContext information across
 * delayed Workflow steps. The last step before a wait or work item form should store
 * the context, then the first step after the wait or form should restore the context.
 */
@Experimental
public class ContextCapture {
    /**
     * Restores the ThreadContext from the WorkflowContext. This should be called in the first step after a wait
     * or work item form, and should be paired with a prior call to storeContext in the last step before the wait
     * or form. The returned object is (optionally) a Runnable that should be invoked when the MDC context is no longer
     * needed, to clean it up.
     *
     * @param wfcontext the WorkflowContext from which to restore the ThreadContext
     * @return a Runnable that should be invoked to clean up the restored ThreadContext when it is no longer needed, or null if there was no context to restore
     */
    @SuppressWarnings("unchecked")
    public static Runnable restoreContext(WorkflowContext wfcontext) {
        Workflow workflow = wfcontext.getWorkflow();

        Object threadContext = workflow.get("iiqcThreadContext");
        if (!(threadContext instanceof Map)) {
            return null;
        }

        MDC.MDCContext context = MDC.start(SailPointObject::getName);

        Runnable closer = context::close;

        ((Map<String, String>) threadContext).forEach(context::put);

        return closer;
    }

    public static void storeContext(WorkflowContext wfcontext) {
        Workflow workflow = wfcontext.getWorkflow();

        ThreadContext.ContextStack contextStack = ThreadContext.getImmutableStack();
        Map<String, String> threadContext = ThreadContext.getImmutableContext();

        if (contextStack != null) {
            List<String> stack = new ArrayList<>(contextStack.asList());
            workflow.put("iiqcThreadContextStack", stack);
        }

        if (threadContext != null) {
            Map<String, String> copy = Map.copyOf(threadContext);
            workflow.put("iiqcThreadContext", copy);
        }

        // This thread will shortly be reused shortly for another Workflow, so we need to clear
        // the ThreadContext to avoid accidentally leaking information between Workflows.

        ThreadContext.clearAll();
    }

}
