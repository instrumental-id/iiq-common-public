package com.identityworksllc.iiq.common.logging;

import org.apache.logging.log4j.ThreadContext;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;

import java.util.*;

/**
 * Utility class for managing MDC contexts. This is designed to be used in a try-with-resources block,
 * and will automatically clean up the MDC context when closed.
 */
public class MDC {

    /**
     * A managed MDC context that allows you to add key-value pairs and NDC entries that will be
     * automatically cleaned up when the context is closed.
     */
    public interface MDCContext extends AutoCloseable {
        /**
         * Closes the MDCContext, removing any keys that were added to the MDC context and popping any NDC entries
         * that were pushed onto the stack. It also restores the MDC context to its previous state using the snapshot
         * taken when the MDCContext was created.
         */
        void close();

        /**
         * Adds an "unknown" identity to the MDC context under the specified prefix. This is used when we want to
         * indicate that an identity is involved in a logged event, but we don't have any information about that
         * identity to put in the MDC context.
         *
         * @param prefix The prefix to use for the MDC keys (e.g., "source" or "target"). The keys added to the
         *               MDC context will be in the format "{prefix}", "{prefix}:id", and "{prefix}:displayName".
         */
        void identityUnknown(String prefix);

        /**
         * Adds an identity to the MDC context under the specified prefix. This adds the identity's name (as calculated
         * by the MDCCalculator) under the key "{prefix}", the identity's ID under the key "{prefix}:id", and
         * the identity's display name under the key "{prefix}:displayName". If the identity is null, it adds
         * an "unknown" identity to the MDC context instead.
         *
         * @param prefix The prefix to use for the MDC keys (e.g., "source" or "target")
         * @param identity The identity to add to the MDC context
         * @throws GeneralException if there is an error calculating the identity's name using the MDCCalculator
         */
        void identity(String prefix, Identity identity) throws GeneralException;

        /**
         * Removes the last NDC from the stack.
         */
        void pop();

        /**
         * Pushes an NDC entry onto the stack and records it in the list of pushed NDCs. This ensures that we can pop
         * the NDC entry when we close the MDCContext, without affecting any NDC entries that were already
         * present on the stack before we started.
         *
         * @param ndc The NDC entry to push onto the stack
         */
        void push(String ndc);

        /**
         * Adds a key-value pair to the MDC context and records the key in the set of added keys.
         *
         * @param key The key to add to the MDC context
         * @param value The value to associate with the key in the MDC context
         */
        void put(String key, String value);

        /**
         * Adds all key-value pairs from the provided context to the MDC context, and records all added keys
         * in the set of added keys.
         *
         * @param context A map of key-value pairs to add to the MDC context
         */
        void putAll(Map<String, String> context);

        /**
         * Removes a key from the MDC context and from the set of added keys.
         *
         * @param key The key to remove from the MDC context
         */
        void remove(String key);

        /**
         * Adds a source identity to the MDC context using the "source" prefix.
         *
         * @param who The source identity to add to the MDC context
         * @throws GeneralException if there is an error calculating the identity's name using the MDCCalculator
         */
        void source(Identity who) throws GeneralException;

        /**
         * Adds a target identity to the MDC context using the "target" prefix.
         *
         * @param target The target identity to add to the MDC context
         * @throws GeneralException if there is an error calculating the identity's name using the MDCCalculator
         */
        void target(Identity target) throws GeneralException;
    }

    /**
     * This class manages an MDC context, allowing you to add key-value pairs and NDC entries that will
     * be automatically cleaned up when the context is closed.
     *
     * @see MDCContext
     */
    public static class MDCContextImpl implements MDCContext, AutoCloseable {
        /**
         * A set of keys that have been added to the MDC context. This is used to ensure that we only remove
         * keys that we have added, and not any keys that were already present in the MDC context before we started.
         */
        private final Set<String> addedKeys;

        /**
         * A calculator for generating usernames from Identity objects. This is used to populate the MDC
         * context with meaningful information about the identities involved in the logged events (e.g., a
         * university username and not a random UUID).
         */
        private final MDCCalculator nameCalculator;

        /**
         * A snapshot of the MDC context before we started. This is used to restore the MDC context to its
         * previous state when we close the context.
         */
        private final Map<String, String> previousContext;

        /**
         * A list of NDC entries that have been pushed onto the stack. This is used to ensure that we only pop
         * NDC entries that we have pushed, and not any entries that were already present in the NDC stack
         * before we started.
         */
        private final List<String> pushedNDCs;

        /**
         * Creates a new MDCContext, taking a snapshot of the current MDC context and initializing the set
         * of added keys and list of pushed NDCs.
         *
         * @param nameCalculator A calculator for generating usernames from Identity objects, used to populate the MDC
         *                       context with meaningful information about the identities involved in the logged events.
         */
        private MDCContextImpl(MDCCalculator nameCalculator) {
            previousContext = ThreadContext.getImmutableContext();
            addedKeys = new HashSet<>();
            pushedNDCs = new ArrayList<>();
            this.nameCalculator = nameCalculator;

            // IIQ leaves these lingering around. Strip them out to avoid weirdness.
            ThreadContext.remove("AppType");
            ThreadContext.remove("Application");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            for (String key : addedKeys) {
                ThreadContext.remove(key);
            }
            for (String ndc : pushedNDCs) {
                ThreadContext.pop();
            }
            if (previousContext != null) {
                ThreadContext.putAll(previousContext);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void identityUnknown(String prefix) {
            put(prefix, "unknown");
        }

        /**
         * {@inheritDoc}
         */
        public void identity(String prefix, Identity identity) throws GeneralException {
            if (identity != null) {
                put(prefix, nameCalculator.getMDCAuditName(identity));
                if (identity.getDisplayName() != null) {
                    put(prefix + ":id", identity.getId());
                    put(prefix + ":displayName", identity.getDisplayName());
                }
            } else {
                identityUnknown(prefix);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void pop() {
            ThreadContext.pop();
            // Remove the last pushed NDC from the list, since we're popping it off the stack
            if (!pushedNDCs.isEmpty()) {
                pushedNDCs.remove(pushedNDCs.size() - 1);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void push(String ndc) {
            ThreadContext.push(ndc);
            pushedNDCs.add(ndc);
        }

        /**
         * {@inheritDoc}
         */
        public void put(String key, String value) {
            ThreadContext.put(key, value);
            addedKeys.add(key);
        }

        /**
         * {@inheritDoc}
         */
        public void putAll(Map<String, String> context) {
            ThreadContext.putAll(context);
            addedKeys.addAll(context.keySet());
        }

        /**
         * {@inheritDoc}
         */
        public void remove(String key) {
            ThreadContext.remove(key);
            addedKeys.remove(key);
        }

        /**
         * {@inheritDoc}
         */
        public void source(Identity who) throws GeneralException {
            identity(LoggingConstants.LOG_MDC_USER_SOURCE, who);
        }

        /**
         * {@inheritDoc}
         */
        public void target(Identity target) throws GeneralException {
            identity(LoggingConstants.LOG_MDC_USER_TARGET, target);
        }
    }
    private static final SLogger log = new SLogger(MDC.class);

    public static MDCContext start(MDCCalculator nameCalculator) {
        String uuid = UUID.randomUUID().toString();
        return start(uuid, nameCalculator);
    }

    public static MDCContext start(String uuid, MDCCalculator nameCalculator) {
        MDCContext context = new MDCContextImpl(nameCalculator);
        context.push("ctx:" + uuid);
        return context;
    }


    public interface MDCCalculator {
        String getMDCAuditName(Identity identity) throws GeneralException;
    }
}
