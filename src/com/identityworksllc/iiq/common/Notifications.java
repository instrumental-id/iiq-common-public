package com.identityworksllc.iiq.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This class implements a set of utilities for notifications. These can be somewhat
 * unwieldy in Sailpoint for straightforward operations like "send an email template
 * with these arguments to one person".
 */
@SuppressWarnings("unused")
public class Notifications {

    private static final Log log = LogFactory.getLog(Notifications.class);

    /**
     * Sends an email notification to the given users with the given arguments
     * @param context A sailpoint context
     * @param singleRecipient The recipient, which can be a user or a workgroup or an email address
     * @param emailTemplate The email template to send
     * @param parameters The parameters to use for the template
     * @throws GeneralException if a send failure occurs
     */
    public static void notify(SailPointContext context, String singleRecipient, String emailTemplate, Map<String, Object> parameters) throws GeneralException {
        notify(context, singleRecipient, null, emailTemplate, parameters);
    }

    /**
     * Sends an email notification to the given users with the given arguments
     * @param context A sailpoint context
     * @param singleRecipient The recipient, which can be a user or a workgroup or an email address
     * @param cc Optional user or email to CC the email to
     * @param emailTemplate The email template to send
     * @param parameters The parameters to use for the template
     * @throws GeneralException if a send failure occurs
     */
    public static void notify(SailPointContext context, String singleRecipient, String cc, String emailTemplate, Map<String, Object> parameters) throws GeneralException {
        notify(context, Arrays.asList(singleRecipient), cc, emailTemplate, parameters);
    }

    /**
     * Sends an email notification to the given users with the given arguments
     * @param context A sailpoint context
     * @param recipients The recipients, which can be a user or a workgroup
     * @param cc Optional user or email to CC the email to
     * @param emailTemplate The email template to send
     * @param parameters The parameters to use for the template
     * @throws GeneralException if a send failure occurs
     */
    public static void notify(SailPointContext context, List<String> recipients, String cc, String emailTemplate, Map<String, Object> parameters) throws GeneralException {
        List<String> toAddresses = new ArrayList<>();
        for(String recipient : Util.safeIterable(recipients)) {
            Identity recipientObject = context.getObjectByName(Identity.class, recipient);
            if (recipientObject != null) {
                List<String> effectiveEmails = ObjectUtil.getEffectiveEmails(context, recipientObject);
                if (effectiveEmails != null) {
                    toAddresses.addAll(ObjectUtil.getEffectiveEmails(context, recipientObject));
                }
            } else {
                if (recipient.contains("@")) {
                    toAddresses.addAll(Arrays.asList(recipient.split("\\s*,\\s*")));
                }
            }
        }
        if (toAddresses.isEmpty()) {
            return;
        }
        String ccAddress = null;
        if (cc != null) {
            Identity recipientObject = context.getObjectByName(Identity.class, cc);
            if (recipientObject != null) {
                ccAddress = recipientObject.getEmail();
            } else {
                if (cc.contains("@")) {
                    ccAddress = cc;
                }
            }
        }
        EmailTemplate template = context.getObjectByName(EmailTemplate.class, emailTemplate);
        if (template == null) {
            throw new GeneralException("Unable to locate an email template called [" + emailTemplate + "]");
        }

        EmailOptions options = new EmailOptions();
        options.setTo(toAddresses);
        if (ccAddress != null) {
            options.setCc(ccAddress);
        }
        options.addVariables(parameters);
        options.setNoRetry(false);
        options.setSendImmediate(false);
        context.sendEmailNotification(template, options);
    }

}
