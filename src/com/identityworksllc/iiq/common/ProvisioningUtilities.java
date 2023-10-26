package com.identityworksllc.iiq.common;

import sailpoint.api.IdentityService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.*;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utilities to wrap the several provisioning APIs available in SailPoint.
 */
@SuppressWarnings("unused")
public class ProvisioningUtilities extends AbstractBaseUtility {

	/**
	 * The attribute for provisioning assigned roles
	 */
	public static final String ASSIGNED_ROLES_ATTR = "assignedRoles";

	/**
	 * The constant to use for no approvals
	 */
	public static final String NO_APPROVAL_SCHEME = "none";

	/**
	 * The approval scheme workflow parameter
	 */
	public static final String PLAN_PARAM_APPROVAL_SCHEME = "approvalScheme";

	/**
	 * The notification scheme workflow parameter
	 */
	public static final String PLAN_PARAM_NOTIFICATION_SCHEME = "notificationScheme";

	/**
	 * Modifies the plan to add the given user to the given role, associating it statically
	 * with the given target accounts (or new accounts if none are specified).
	 *
	 * @param context the IIQ context
	 * @param identityName The identity name to add to the given role
	 * @param roleName The role to add
	 * @param targets These Links will be used as a provisioning target for the plan. If a value is null, a new account create will be requested.
	 * @param provisioningPlan The provisioning plan to modify
	 * @throws GeneralException if a failure occurs while looking up required objects
	 */
	public static void addUserRolePlan(SailPointContext context, String identityName, String roleName, Map<String, Link> targets, ProvisioningPlan provisioningPlan) throws GeneralException {
		Objects.requireNonNull(identityName);
		Objects.requireNonNull(roleName);

		// We have to generate our own assignment ID in this case
		String assignmentKey = Util.uuid();
		Bundle role = context.getObjectByName(Bundle.class, roleName);

		if (role == null) {
			throw new IllegalArgumentException("Role " + roleName + " does not exist");
		}

		Identity planIdentity = provisioningPlan.getIdentity();

		AccountRequest accountRequest = new AccountRequest(AccountRequest.Operation.Modify, ProvisioningPlan.APP_IIQ, null, planIdentity.getName());
		AttributeRequest attributeRequest = new AttributeRequest(ASSIGNED_ROLES_ATTR, ProvisioningPlan.Operation.Add, roleName);
		attributeRequest.setAssignmentId(assignmentKey);
		accountRequest.add(attributeRequest);
		provisioningPlan.add(accountRequest);

		List<ProvisioningTarget> provisioningTargetSelectors = new ArrayList<>();

		for(String appName : targets.keySet()) {
			Application application = context.getObjectByName(Application.class, appName);
			if (application == null) {
				throw new IllegalArgumentException("Application " + appName + " passed in the target map does not exist");
			}
			AccountSelection selection = new AccountSelection(application);;
			Link target = targets.get(appName);
			if (target == null) {
				selection.setAllowCreate(true);
				selection.setRoleName(roleName);
				selection.setDoCreate(true);
			} else {
				RoleTarget roleTarget = new RoleTarget(target);
				selection.setAllowCreate(false);
				selection.setRoleName(roleName);
				selection.addAccountInfo(target);
				selection.setSelection(roleTarget.getNativeIdentity());
			}
			ProvisioningTarget provisioningTarget = new ProvisioningTarget(assignmentKey, role);
			provisioningTarget.setRole(roleName);
			provisioningTarget.setApplication(appName);
			provisioningTarget.addAccountSelection(selection);
			provisioningTargetSelectors.add(provisioningTarget);
		}

		provisioningPlan.setProvisioningTargets(provisioningTargetSelectors);
	}

	/**
	 * Gets the arguments for the given plan, creating one if needed
	 * @param plan The request
	 * @return The arguments for the plan
	 */
	public static Attributes<String, Object> getArguments(ProvisioningPlan plan) {
		Attributes<String, Object> arguments = plan.getArguments();
		if (arguments == null) {
			arguments = new Attributes<>();
			plan.setArguments(arguments);
		}
		return arguments;
	}

	/**
	 * Gets the arguments for the given request, creating one if needed
	 * @param request The request
	 * @return The arguments for the request
	 */
	public static Attributes<String, Object> getArguments(ProvisioningPlan.AbstractRequest request) {
		Attributes<String, Object> arguments = request.getArguments();
		if (arguments == null) {
			arguments = new Attributes<>();
			request.setArguments(arguments);
		}
		return arguments;
	}

	/**
	 * Gets the IIQ account request from the given plan, creating one if needed
	 * @param plan The plan in question
	 * @return The IIQ account request
	 */
	public static AccountRequest getIIQAccountRequest(ProvisioningPlan plan) {
		AccountRequest accountRequest = plan.getIIQAccountRequest();
		if (accountRequest == null) {
			accountRequest = new AccountRequest();
			accountRequest.setOperation(AccountRequest.Operation.Modify);
			accountRequest.setApplication(ProvisioningPlan.APP_IIQ);
			plan.add(accountRequest);
		}
		return accountRequest;
	}

	/**
	 * Intended to be used in a pre or post-provision rule, this method will return the given attribute from the AccountRequest if present and otherwise will return it from the Link. The Link will be looked up based on the contents of the AccountRequest.
	 * @param req The AccountRequest modifying this Link
	 * @param name The name of the attribute to return
	 * @return the attribute value
	 * @throws GeneralException if a query failure occurs
	 */
	public static Object getLatestValue(SailPointContext context, AccountRequest req, String name) throws GeneralException {
        Link account = getLinkFromRequest(context, req);
        return getLatestValue(account, req, name);
	}

	/**
	 * Intended to be used in a pre or post-provision rule, this method will return the given attribute from the AccountRequest if present and otherwise will return it from the Link
	 * @param account The Link being modified
	 * @param req The AccountRequest modifying this Link
	 * @param name The name of the attribute to return
	 * @return the attribute value
	 */
	public static Object getLatestValue(Link account, AccountRequest req, String name) {
		Object value = null;

		AttributeRequest attr = req.getAttributeRequest(name);
		if (attr != null) {
			value = attr.getValue();
		} else if (account != null) {
			value = account.getAttributes().get(name);
		}
		return value;
	}

	/**
	 * Retrieves the Link that is associated with the given AccountRequest, or returns null
	 * if no link can be found. The Application on the request must be set and accurate.
	 *
	 * On create, the outcome will always be null because the Link doesn't exist until after
	 * the operation has completed.
	 *
	 * @param request The request to use to search
	 * @return the matching Link, or null if none can be found
	 * @throws GeneralException if more than one matching Link is found
	 */
	public static Link getLinkFromRequest(SailPointContext context, AccountRequest request) throws GeneralException {
		if (request == null || Util.isNullOrEmpty(request.getApplication()) || Util.nullSafeEq(request.getApplication(), ProvisioningPlan.APP_IIQ)) {
			return null;
		}
		Application app = request.getApplication(context);
		if (app != null) {
			Filter filter = Filter.and(Filter.eq("application.name", app.getName()), Filter.eq("nativeIdentity", request.getNativeIdentity()));
			QueryOptions qo = new QueryOptions();
			qo.add(filter);

			List<Link> objects = context.getObjects(Link.class, qo);
			if (objects.size() == 1) {
				return objects.get(0);
			} else if (objects.size() == 0) {
				return null;
			} else {
				String message = "Expected to find 1 Link with application = " + app.getName() + " and native identity = " + request.getNativeIdentity() + ", but found " + objects.size();
				throw new GeneralException(message);
			}
		}
		return null;
	}

	/**
	 * Creates an AttributeRequest to move the given Link to the given target Identity,
	 * either modifying the provided plan or creating a new one.
	 *
	 * A move-account plan can be structured in either direction. It can be an "Add"
	 * plan that focuses on the destination Identity (allowing movement of accounts
	 * from more than one source) or a "Remove" plan that focuses on the source Identity
	 * (allowing movement of accounts to more than one target). You cannot mix these
	 * on a single plan.
	 *
	 * If the plan does not already contain a link move, it will be set up as an Add.
	 *
	 * This method will throw an exception if you pass an existing plan and its structure
	 * does not match the objects you pass in.
	 *
	 * @param theLinkToMove The link to move
	 * @param targetIdentity The Identity to which the link should be moved
	 * @param existingPlan The existing plan to modify, or null to create a new one
	 * @return The plan created or modified by this method
	 * @throws GeneralException if any validation failures occur
	 */
	@SuppressWarnings("unused")
	public static ProvisioningPlan linkMovePlan(Link theLinkToMove, Identity targetIdentity, ProvisioningPlan existingPlan) throws GeneralException {
		if (theLinkToMove == null) {
			throw new GeneralException("The link to move cannot be null");
		}

		if (targetIdentity == null || Util.isNullOrEmpty(targetIdentity.getId())) {
			throw new GeneralException("The target Identity must be not be null and must have an ID");
		}

		Identity sourceIdentity = theLinkToMove.getIdentity();
		if (sourceIdentity == null) {
			throw new GeneralException("Link " + theLinkToMove.getId() + " does not appear to have a valid Identity attached??");
		}

		if (Util.nullSafeEq(sourceIdentity.getId(), targetIdentity.getId())) {
			throw new GeneralException("Source and target Identity are the same");
		}

		// Add or Remove, depending on how the existing plan is structured (default Add)
		ProvisioningPlan.Operation op;

		// The identity ID we expect to find in the existing IIQ AccountRequest, if one exists
		String expectedAccountRequestIdentity;

		ProvisioningPlan thePlan = existingPlan;
		if (thePlan == null) {
			thePlan = new ProvisioningPlan();
			thePlan.setComments("Move account " + theLinkToMove.getId() + " via API");
		}

		if (thePlan.getIdentity() == null) {
			// Move-to plan
			thePlan.setIdentity(targetIdentity);
			op = ProvisioningPlan.Operation.Add;
			expectedAccountRequestIdentity = targetIdentity.getId();
		} else {
			Identity existingIdentity = thePlan.getIdentity();
			if (Util.nullSafeEq(existingIdentity.getId(), sourceIdentity.getId())) {
				// Move-from plan
				op = ProvisioningPlan.Operation.Remove;
				expectedAccountRequestIdentity = sourceIdentity.getId();
			} else if (Util.nullSafeEq(existingIdentity.getId(), targetIdentity.getId())) {
				// Move-to plan
				op = ProvisioningPlan.Operation.Add;
				expectedAccountRequestIdentity = targetIdentity.getId();
			} else {
				// We don't know who this is, error
				throw new GeneralException("The ProvisioningPlan's associated Identity is neither the specified source nor target; cannot construct a link move plan");
			}
		}

		AccountRequest iiqRequest = thePlan.getIIQAccountRequest();
		if (iiqRequest == null) {
			iiqRequest = new AccountRequest();
			iiqRequest.setOperation(AccountRequest.Operation.Modify);
			iiqRequest.setApplication(ProvisioningPlan.APP_IIQ);
			iiqRequest.setNativeIdentity(expectedAccountRequestIdentity);

			thePlan.add(iiqRequest);
		} else {
			String nativeIdentity = iiqRequest.getNativeIdentity();
			if (!Util.nullSafeEq(nativeIdentity, expectedAccountRequestIdentity)) {
				// We don't know who this is, error
				throw new GeneralException("The plan's 'IIQ' AccountRequest already has target Identity [" + nativeIdentity + "], which is not the same as the expected Identity ID [" + expectedAccountRequestIdentity + "]. A move-account plan can only move to one Identity or from one Identity.");
			}
		}

		AttributeRequest attributeRequest = new AttributeRequest(ProvisioningPlan.ATT_IIQ_LINKS, op, theLinkToMove.getId());
		if (op == ProvisioningPlan.Operation.Add) {
			// Move from this source
			attributeRequest.put(ProvisioningPlan.ARG_SOURCE_IDENTITY, sourceIdentity.getId());
		} else {
			// Move to this target
			attributeRequest.put(ProvisioningPlan.ARG_DESTINATION_IDENTITY, targetIdentity.getId());
		}
		iiqRequest.add(attributeRequest);

		return thePlan;
	}

	/**
	 * Modifies the plan to add a role removal request for the given role
	 * @param roleName The role to remove from the identity
	 * @param revoke If true, the role will be revoked and not removed
	 * @param provisioningPlan The plan to add the role removal to
	 * @throws GeneralException If a failure occurs
	 */
	public static void removeUserRolePlan(String roleName, boolean revoke, ProvisioningPlan provisioningPlan) throws GeneralException {
		Objects.requireNonNull(roleName);
		if (roleName.trim().isEmpty()) {
			throw new IllegalArgumentException("A non-empty role name must be provided");
		}
		Objects.requireNonNull(provisioningPlan);
		AccountRequest accountRequest = provisioningPlan.getIIQAccountRequest();
		if (accountRequest == null) {
			accountRequest = new AccountRequest();
			provisioningPlan.add(accountRequest);
		}
		accountRequest.setOperation(AccountRequest.Operation.Modify);
		accountRequest.setApplication(ProvisioningPlan.APP_IIQ);
		AttributeRequest attributeRequest = new AttributeRequest();
		attributeRequest.setOperation(revoke ? ProvisioningPlan.Operation.Revoke : ProvisioningPlan.Operation.Remove);
		attributeRequest.setName(ASSIGNED_ROLES_ATTR);
		attributeRequest.setValue(roleName);
		List<AttributeRequest> requestList = new ArrayList<>();
		requestList.add(attributeRequest);
		accountRequest.setAttributeRequests(requestList);
		provisioningPlan.add(accountRequest);
	}

	/**
	 * Creates a AccountSelection object from the given account selection
	 * @param target The target to transform
	 * @return An {@link AccountSelection} with the given RoleTarget parameters
	 */
	public static AccountSelection roleTargetToAccountSelection(RoleTarget target) {
		AccountSelection selection = new AccountSelection();
		selection.addAccountInfo(target);
		return selection;
	}

	/**
	 * Creates a Provisioning Target from account
	 * @param role The role being provisioned
	 * @param target The target account
	 * @return A ProvisioningTarget object for the given role / account combination
	 */
	public static ProvisioningTarget toProvisioningTarget(Bundle role, Link target) {
		String assignmentKey = Util.uuid();
		AccountSelection selection = new AccountSelection(target.getApplication());
		RoleTarget roleTarget = new RoleTarget(target);
		selection.setAllowCreate(false);
		selection.setRoleName(role.getName());
		selection.addAccountInfo(target);
		selection.setSelection(roleTarget.getNativeIdentity());
		ProvisioningTarget provisioningTarget = new ProvisioningTarget(assignmentKey, role);
		provisioningTarget.setRole(role.getName());
		provisioningTarget.setApplication(target.getApplicationName());
		provisioningTarget.addAccountSelection(selection);
		return provisioningTarget;
	}

	/**
	 * Creates a Provisioning Target from the given application and nativeIdentity name
	 * @param role The role being provisioned
	 * @param application The application to target
	 * @param nativeIdentity The native identity to target
	 * @return A ProvisioningTarget object for the given role / account combination
	 * @throws GeneralException if any failures occur
	 */
	public static ProvisioningTarget toProvisioningTarget(SailPointContext context, Bundle role, String application, String nativeIdentity) throws GeneralException {
		String assignmentKey = Util.uuid();
		Application applicationObj = context.getObjectByName(Application.class, application);
		AccountSelection selection = new AccountSelection(applicationObj);
		RoleTarget roleTarget = new RoleTarget();
		roleTarget.setApplicationName(application);
		roleTarget.setRoleName(role.getName());
		if (Util.isNotNullOrEmpty(nativeIdentity)) {
			roleTarget.setNativeIdentity(nativeIdentity);
		} else {
			selection.setAllowCreate(true);
		}
		selection.setRoleName(role.getName());
		selection.addAccountInfo(roleTarget);
		selection.setSelection(nativeIdentity);
		ProvisioningTarget provisioningTarget = new ProvisioningTarget(assignmentKey, role);
		provisioningTarget.setRole(role.getName());
		provisioningTarget.setApplication(application);
		provisioningTarget.addAccountSelection(selection);
		return provisioningTarget;
	}
	/**
	 * Invoked in the final tier of doProvisioning if present, mainly for testing purposes
	 */
	private Consumer<ProvisioningPlan> beforeProvisioningConsumer;
	/**
	 * If not null, this ticket ID will be attached to any provisioning operation
	 */
	private String externalTicketId;
	/**
	 * Any plan arguments passed to the provisioner
	 */
	private final Attributes<String, Object> planArguments;
	/**
	 * Invoked with the compiled project just before provisioning
	 */
	private Consumer<ProvisioningProject> projectDebugger;
	/**
	 * The workflow configuration container
	 */
	private final ProvisioningArguments provisioningArguments;
	/**
	 * Invoked with the compiled project just before provisioning
	 */
	private Consumer<WorkflowLaunch> workflowDebugger;

	/**
	 * Constructs a workflow-based Provisioning Utilities that will use the default
	 * LCM Provisioning workflow for all operations.
	 *
	 * @param c The SailPoint context
	 */
	public ProvisioningUtilities(SailPointContext c) {
		super(Objects.requireNonNull(c));
		provisioningArguments = new ProvisioningArguments();
		planArguments = new Attributes<>();
	}

	public ProvisioningUtilities(SailPointContext context, ProvisioningArguments arguments) {
		this(context);

		if (arguments != null) {
			this.provisioningArguments.merge(arguments);
		}
	}

	@SuppressWarnings("unchecked")
	public ProvisioningUtilities(SailPointContext context, Map<String, Object> arguments) throws GeneralException {
		this(context);

		if (arguments == null) {
			throw new IllegalArgumentException("Invalid input to ProvisioningUtilities: cannot provide a null Map for arguments");
		}

		Attributes<String, Object> map = new Attributes<>(arguments);

		provisioningArguments.setErrorOnAccountSelection(map.getBoolean("errorOnAccountSelection"));
		provisioningArguments.setErrorOnManualTask(map.getBoolean("errorOnManualTask"));
		provisioningArguments.setErrorOnNewAccount(map.getBoolean("errorOnNewAccount"));
		provisioningArguments.setErrorOnProvisioningForms(map.getBoolean("errorOnProvisioningForms"));

		this.externalTicketId = map.getString("externalTicketId");

		provisioningArguments.setPlanFieldName(map.getString("workflowPlanField"));
		provisioningArguments.setIdentityFieldName(map.getString("workflowIdentityField"));
		provisioningArguments.setCaseNameTemplate(map.getString("caseNameTemplate"));
		if (map.get("provisioningWorkflow") instanceof String) {
			provisioningArguments.setWorkflowName(map.getString("provisioningWorkflow"));
		}
		provisioningArguments.setUseWorkflow(map.getBoolean("useWorkflow", true));
		if (map.get("defaultExtraParameters") instanceof Map) {
			provisioningArguments.setDefaultExtraParameters((Map<String, Object>) map.get("defaultExtraParameters"));
		}
		if (map.get("planArguments") instanceof Map) {
			Map<String, Object> tempMap = (Map<String, Object>) map.get("planArguments");
			this.planArguments.putAll(tempMap);
		}
	}

	/**
	 * Constructs a Provisioning Utilities that will optionally directly forward provisioning operations to the Provisioner.
	 * @param c The SailPoint context
	 * @param useWorkflow If true, workflows will be bypassed and provisioning will be sent directly to the provisioner
	 */
	public ProvisioningUtilities(SailPointContext c, boolean useWorkflow) {
		this(c);
		provisioningArguments.setUseWorkflow(true);
	}

	/**
	 * Constructs a workflow-based Provisioning Utilities that will use the given workflow instead of the default
	 * @param c The SailPoint context
	 * @param provisioningWorkflowName The name of a provisioning workflow which should expect an 'identityName' and 'plan' attribute
	 */
	public ProvisioningUtilities(SailPointContext c, String provisioningWorkflowName) {
		this(c, true);
		provisioningArguments.setWorkflowName(provisioningWorkflowName);
	}

	/**
	 * Constructs a Provisioning Utilities that will optionally directly forward provisioning operations to the Provisioner, or else will use the given provisioning workflow
	 * @param c The SailPoint context
	 * @param provisioningWorkflowName The name of a provisioning workflow which should expect an 'identityName' and 'plan' attribute
	 * @param useWorkflow If true, workflows will be bypassed and provisioning will be sent directly to the provisioner
	 */
	public ProvisioningUtilities(SailPointContext c, String provisioningWorkflowName, boolean useWorkflow) {
		this(c, provisioningWorkflowName);
		provisioningArguments.setUseWorkflow(useWorkflow);
	}

	/**
	 * Adds the given entitlement to the given account on the user
	 * @param identityName The identity name
	 * @param account The account to modify
	 * @param entitlement The managed attribute from which to extract the entitlement
	 * @param withApproval If false, approval will be skipped
	 * @throws GeneralException if any failure occurs
	 */
	public void addEntitlement(String identityName, Link account, ManagedAttribute entitlement, boolean withApproval) throws GeneralException {
		ProvisioningPlan plan = new ProvisioningPlan();
		Identity identity = findIdentity(identityName);
		plan.setIdentity(identity);

		AccountRequest accountRequest = new AccountRequest(AccountRequest.Operation.Modify, account.getApplicationName(), account.getInstance(), account.getNativeIdentity());
		plan.add(accountRequest);

		AttributeRequest attributeRequest = new AttributeRequest(entitlement.getName(), ProvisioningPlan.Operation.Add, entitlement.getValue());
		accountRequest.add(attributeRequest);

		Map<String, Object> options = new HashMap<>();
		if (!withApproval) {
			options.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		}

		doProvisioning(identityName, plan, false, options);
	}

	/**
	 * Adds the given entitlement to the given account on the user
	 * @param identityName The identity name
	 * @param account The account to modify
	 * @param attribute The attribute to modify
	 * @param value The value to add
	 * @param withApproval If false, approval will be skipped
	 * @throws GeneralException if any failure occurs
	 */
	public void addEntitlement(String identityName, Link account, String attribute, String value, boolean withApproval) throws GeneralException {
		ProvisioningPlan plan = new ProvisioningPlan();
		Identity identity = findIdentity(identityName);
		plan.setIdentity(identity);

		AccountRequest accountRequest = new AccountRequest(AccountRequest.Operation.Modify, account.getApplicationName(), account.getInstance(), account.getNativeIdentity());
		plan.add(accountRequest);

		AttributeRequest attributeRequest = new AttributeRequest(attribute, ProvisioningPlan.Operation.Add, value);
		accountRequest.add(attributeRequest);

		Map<String, Object> options = new HashMap<>();
		if (!withApproval) {
			options.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		}

		doProvisioning(identityName, plan, false, options);
	}

	/**
	 * Adds an argument to the ProvisioningPlan that will eventually be constructed
	 * on a call to {@link #doProvisioning(ProvisioningPlan)}
	 * @param argument The argument to add to the plan
	 * @param value The value to add to the plan
	 */
	public void addPlanArgument(String argument, Object value) {
		planArguments.put(argument, value);
	}

	/**
	 * Adds the given user to the given role, guessing the target account by name. If the
	 * plan expands to more than one account selection question, this method will throw
	 * an exception.
	 *
	 * @param identityName The identity name to add to the given role
	 * @param roleName The role to add
	 * @param withApproval If true, default approval will be required
	 * @param accountName The target account to locate
	 * @throws GeneralException if a provisioning failure occurs
	 */
	public void addUserRole(String identityName, String roleName, boolean withApproval, String accountName) throws GeneralException {
		Objects.requireNonNull(identityName, "The passed identityName must not be null");
		Objects.requireNonNull(roleName, "The passed roleName must not be null");
		if (Util.isNullOrEmpty(accountName)) {
			// No sense going through the below if we didn't pass an account name
			addUserRole(identityName, roleName, withApproval);
			return;
		}
		Bundle role = context.getObjectByName(Bundle.class, roleName);
		if (role == null) {
			throw new IllegalArgumentException("Role " + roleName + " does not exist");
		}
		// We have to generate our own assignment ID in this case
		String assignmentKey = Util.uuid();
		ProvisioningPlan provisioningPlan = new ProvisioningPlan();
		Identity identity = findIdentity(identityName);
		provisioningPlan.setIdentity(identity);
		AccountRequest accountRequest = new AccountRequest(AccountRequest.Operation.Modify, ProvisioningPlan.APP_IIQ, null, identity.getName());
		AttributeRequest attributeRequest = new AttributeRequest(ASSIGNED_ROLES_ATTR, ProvisioningPlan.Operation.Add, roleName);
		attributeRequest.setAssignmentId(assignmentKey);
		accountRequest.add(attributeRequest);
		provisioningPlan.add(accountRequest);

		PlanCompiler compiler = new PlanCompiler(context);
		ProvisioningProject project = compiler.compile(new Attributes<>(), provisioningPlan, new Attributes<>());
		List<ProvisioningTarget> targets = Utilities.safeStream(project.getProvisioningTargets()).filter(ProvisioningTarget::isAnswered).collect(Collectors.toList());
		if (targets.size() > 1) {
			throw new IllegalStateException("The resulting provisioning project has more than one unanswered account selection");
		} else if (targets.size() == 1) {
			ProvisioningTarget tempTarget = targets.get(0);
			String appName = tempTarget.getApplication();
			Application application = context.getObjectByName(Application.class, appName);
			IdentityService ids = new IdentityService(context);
			List<Link> possibleTargets = ids.getLinks(identity, application);
			Optional<Link> maybeLink =
					Utilities.safeStream(possibleTargets)
							.filter(Functions.isNativeIdentity(accountName))
							.findAny();

			List<ProvisioningTarget> provisioningTargetSelectors = new ArrayList<>();

			AccountSelection selection = new AccountSelection(application);
			if (!maybeLink.isPresent()) {
				selection.setAllowCreate(true);
				selection.setRoleName(roleName);
				selection.setDoCreate(true);
			} else {
				RoleTarget roleTarget = new RoleTarget(maybeLink.get());
				selection.setAllowCreate(false);
				selection.setRoleName(roleName);
				selection.addAccountInfo(roleTarget);
				selection.setSelection(roleTarget.getNativeIdentity());
			}
			ProvisioningTarget provisioningTarget = new ProvisioningTarget(assignmentKey, role);
			provisioningTarget.setRole(roleName);
			provisioningTarget.setApplication(appName);
			provisioningTarget.addAccountSelection(selection);
			provisioningTargetSelectors.add(provisioningTarget);
			provisioningPlan.setProvisioningTargets(provisioningTargetSelectors);
		}
		Map<String, Object> options = new HashMap<>();
		if (!withApproval) {
			options.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		}
		doProvisioning(identity.getName(), provisioningPlan, false, options);
	}

	/**
	 * Adds the given user to the given role, associating it statically with the given
	 * target accounts (or new accounts if none are specified). If a target is not supplied
	 * for a given application that is provisioned by this role, the provisioning engine
	 * will automatically run any account selection rule followed by an attempt at heuristic
	 * guessing.
	 *
	 * @param identityName The identity name to add to the given role
	 * @param roleName The role to add
	 * @param withApproval If true, default approval will be required
	 * @param targets These Links will be used as a provisioning target for the plan. If a value is null, a new account create will be requested.
	 * @throws GeneralException if a provisioning failure occurs
	 */
	public void addUserRole(String identityName, String roleName, boolean withApproval, Map<String, Link> targets) throws GeneralException {
		Objects.requireNonNull(identityName);

		ProvisioningPlan provisioningPlan = new ProvisioningPlan();
		Identity identity = findIdentity(identityName);
		provisioningPlan.setIdentity(identity);

		addUserRolePlan(context, identityName, roleName, targets, provisioningPlan);

		Map<String, Object> options = new HashMap<>();
		if (!withApproval) {
			options.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		}
		doProvisioning(identity.getName(), provisioningPlan, false, options);
	}

	/**
	 * Adds the given user to the given role
	 * @param identityName The identity name to add to the given role
	 * @param roleName The role to add
	 * @throws GeneralException if a provisioning failure occurs
	 */
	public void addUserRole(String identityName, String roleName) throws GeneralException {
		addUserRole(identityName, roleName, false);
	}

	/**
	 * Adds the given user to the given role
	 * @param identityName The identity name to add to the given role
	 * @param roleName The role to add
	 * @param withApproval If true, default approval will be required
	 * @throws GeneralException if a provisioning failure occurs
	 */
	public void addUserRole(String identityName, String roleName, boolean withApproval) throws GeneralException {
        ProvisioningPlan provisioningPlan = new ProvisioningPlan();
        Identity identity = findIdentity(identityName);
        provisioningPlan.setIdentity(identity);
        // This is magical
        provisioningPlan.add(ProvisioningPlan.APP_IIQ, identity.getName(), ASSIGNED_ROLES_ATTR, ProvisioningPlan.Operation.Add, roleName);
        Map<String, Object> options = new HashMap<>();
        if (!withApproval) {
            options.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
        }
        doProvisioning(identity.getName(), provisioningPlan, false, options);
    }

	/**
	 * Adds an argument to the workflow or Provisioner that will be used in a call to
	 * {@link #doProvisioning(ProvisioningPlan)}. If the value provided is null, the
	 * key will be removed from the arguments.
	 *
	 * @param argument The argument to set the value for
	 * @param value The value to set
	 */
	public void addWorkflowArgument(String argument, Object value) {
		if (value == null) {
			provisioningArguments.getDefaultExtraParameters().remove(argument);
		} else {
			provisioningArguments.getDefaultExtraParameters().put(argument, value);
		}
	}

	/**
	 * Deletes the given account by submitting a Delete request to IIQ
	 * @param link The Link to disable
	 * @throws GeneralException if any failures occur
	 */
	public void deleteAccount(Link link) throws GeneralException {
		Objects.requireNonNull(link, "Provided Link must not be null");
		ProvisioningPlan plan = new ProvisioningPlan();
		String username = ObjectUtil.getIdentityFromLink(context, link.getApplication(), link.getInstance(), link.getNativeIdentity());
		Identity user = findIdentity(username);
		plan.setIdentity(user);
		plan.add(link.getApplicationName(), link.getNativeIdentity(), AccountRequest.Operation.Delete);
		Map<String, Object> extraParameters = new HashMap<>();
		extraParameters.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		doProvisioning(user.getName(), plan, false, extraParameters);
	}

	/**
	 * Disables the given account by submitting a Disable request to IIQ
	 * @param link The Link to disable
	 * @throws GeneralException if any failures occur
	 */
    public void disableAccount(Link link) throws GeneralException {
		Objects.requireNonNull(link, "Provided Link must not be null");
		disableAccount(link, false);
	}

	/**
	 * Disables the given account by submitting a Disable request to IIQ
	 * @param link The Link to disable
	 * @throws GeneralException if any failures occur
	 */
	public void disableAccount(Link link, boolean doRefresh) throws GeneralException {
		Objects.requireNonNull(link, "Provided Link must not be null");
		ProvisioningPlan plan = new ProvisioningPlan();
		String username = ObjectUtil.getIdentityFromLink(context, link.getApplication(), link.getInstance(), link.getNativeIdentity());
		Identity user = findIdentity(username);
		plan.setIdentity(user);
		plan.add(link.getApplicationName(), link.getNativeIdentity(), AccountRequest.Operation.Disable);
		Map<String, Object> extraParameters = new HashMap<>();
		extraParameters.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		doProvisioning(user.getName(), plan, doRefresh, extraParameters);
	}

	/**
	 * Submits a single request to disable all accounts on the Identity that are not already disabled.
	 * @param identity Who to disable the accounts on
	 * @throws GeneralException if any failures occur
	 */
	public void disableAccounts(Identity identity) throws GeneralException {
		Objects.requireNonNull(identity, "Provided Identity must not be null");
		ProvisioningPlan plan = new ProvisioningPlan();
		plan.setIdentity(identity);
		for(Link link : Util.safeIterable(identity.getLinks())) {
			if (!link.isDisabled()) {
				plan.add(link.getApplicationName(), link.getNativeIdentity(), AccountRequest.Operation.Disable);
			}
		}
		doProvisioning(plan);
	}

	/**
	 * Submits a single request to disable all accounts on the Identity that are not already disabled.
	 * @param identity Who to disable the accounts on
	 * @param onlyThese Only applications in this list will be disabled
	 * @throws GeneralException if any failures occur
	 */
	public void disableAccounts(Identity identity, List<String> onlyThese) throws GeneralException {
		Objects.requireNonNull(identity, "Provided Identity must not be null");
		ProvisioningPlan plan = new ProvisioningPlan();
		plan.setIdentity(identity);
		for(Link link : Util.safeIterable(identity.getLinks())) {
			if (!link.isDisabled() && Util.nullSafeContains(onlyThese, link.getApplication())) {
				plan.add(link.getApplicationName(), link.getNativeIdentity(), AccountRequest.Operation.Disable);
			}
		}
		doProvisioning(plan);
	}

	/**
	 * Submits a single request to disable all accounts on the Identity that are not already disabled.
	 * @param identity Who to disable the accounts on
	 * @param onlyThese Only Link objects where the Predicate returns true will be disabled
	 * @throws GeneralException if any failures occur
	 */
	public void disableAccounts(Identity identity, Predicate<Link> onlyThese) throws GeneralException {
		Objects.requireNonNull(identity, "Provided Identity must not be null");
		ProvisioningPlan plan = new ProvisioningPlan();
		plan.setIdentity(identity);
		for(Link link : Util.safeIterable(identity.getLinks())) {
			if (!link.isDisabled() && onlyThese.test(link)) {
				plan.add(link.getApplicationName(), link.getNativeIdentity(), AccountRequest.Operation.Disable);
			}
		}
		doProvisioning(plan);
	}

	/**
	 * Submits a single request to disable all accounts on the Identity that are not already disabled.
	 * @param identity Who to disable the accounts on
	 * @param onlyThese Only Link objects matching the filter will be disabled. This uses the {@link HybridObjectMatcher}, allowing fields like "application.name" in the filter.
	 * @throws GeneralException if any failures occur
	 */
	public void disableAccounts(Identity identity, Filter onlyThese) throws GeneralException {
		Objects.requireNonNull(identity, "Provided Identity must not be null");
		ProvisioningPlan plan = new ProvisioningPlan();
		plan.setIdentity(identity);
		for(Link link : Util.safeIterable(identity.getLinks())) {
			HybridObjectMatcher matcher = new HybridObjectMatcher(context, onlyThese);
			if (!link.isDisabled() && matcher.matches(link)) {
				plan.add(link.getApplicationName(), link.getNativeIdentity(), AccountRequest.Operation.Disable);
			}
		}
		doProvisioning(plan);
	}

    /**
     * Submits a provisioning plan using the configured defaults. This plan must have an Identity attached to it using setIdentity().
     * @param plan The ProvisioningPlan to execute
     * @throws GeneralException if any failures occur
     */
    public ProvisioningProject doProvisioning(ProvisioningPlan plan) throws GeneralException {
        return doProvisioning(plan, false, provisioningArguments.getDefaultExtraParameters());
    }

    /**
     * Submits a provisioning plan using the configured defaults.
     * @param identityName If the plan does not already have an Identity configured, this one will be used.
     * @param plan The provisioning plan.
	 * @return The compiled provisioning project, post-provision
     * @throws GeneralException if any failures occur
     */
    public ProvisioningProject doProvisioning(String identityName, ProvisioningPlan plan) throws GeneralException {
        return doProvisioning(identityName, plan, false);
    }

    /**
     * Submits a provisioning plan using the configured defaults and optionally does a refresh.
     * @param identityName If the plan does not already have an Identity configured, this one will be used.
     * @param plan The provisioning plan
     * @param doRefresh If true, a refresh will be performed by the provisioning handler
	 * @return The compiled provisioning project, post-provision
     * @throws GeneralException if any IIQ failures occur
     */
	public ProvisioningProject doProvisioning(String identityName, ProvisioningPlan plan, boolean doRefresh) throws GeneralException {
        return doProvisioning(identityName, plan, doRefresh, provisioningArguments.getDefaultExtraParameters());
    }

	/**
	 * Submits a provisioning plan using the configured defaults and optionally does a refresh. Additionally, extra arguments to the workflow can be provided in a Map.
     * @param identityName If the plan does not already have an Identity configured, this one will be used.
     * @param plan The provisioning plan
     * @param doRefresh If true, a refresh will be performed by the provisioning handler
	 * @param extraParameters A Map containing workflow parameters that will be passed to the provisioning workflow or Provisioner
	 * @return The compiled provisioning project, post-provision
	 * @throws GeneralException if any IIQ failures occur
	 */
	public ProvisioningProject doProvisioning(String identityName, ProvisioningPlan plan, boolean doRefresh, Map<String, Object> extraParameters) throws GeneralException {
		if (plan.getIdentity() == null) {
			plan.setIdentity(context.getObjectByName(Identity.class, identityName));
		}
		return doProvisioning(plan, doRefresh, extraParameters);
	}

	/**
	 * Submits a provisioning plan using the configured defaults and optionally does a refresh. Additionally, extra arguments to the workflow can be provided in a Map.
	 * @param plan The provisioning plan
	 * @param doRefresh If true, a refresh will be performed by the provisioning handler
	 * @param extraParameters A Map containing workflow parameters that will be passed to the provisioning workflow or Provisioner
	 * @throws GeneralException if any IIQ failures occur
	 */
	public ProvisioningProject doProvisioning(ProvisioningPlan plan, boolean doRefresh, Map<String, Object> extraParameters) throws GeneralException {
		Attributes<String, Object> arguments = getArguments(plan);
		if (planArguments != null) {
			arguments.putAll(planArguments);
		}
		plan.setArguments(arguments);
		if (beforeProvisioningConsumer != null) {
			beforeProvisioningConsumer.accept(plan);
		}
		ProvisioningProject outputProject = null;
		if (isErrorOnAccountSelection() || isErrorOnManualTask() || isErrorOnProvisioningForms() || isErrorOnNewAccount() || projectDebugger != null) {
			PlanCompiler compiler = new PlanCompiler(context);
			ProvisioningProject project = compiler.compile(new Attributes<>(extraParameters), plan, new Attributes<>());
			if (projectDebugger != null) {
				projectDebugger.accept(project);
			}
			if (isErrorOnManualTask() && project.hasUnmanagedPlan()) {
				throw new GeneralException("Provisioning request refused because it would result in a manual task");
			}
			if (isErrorOnProvisioningForms() && (project.hasQuestions() || project.hasUnansweredAccountSelections() || project.hasUnansweredProvisioningTargets())) {
				throw new GeneralException("Provisioning request refused because it would result in an unanswered form");
			}
			if (isErrorOnNewAccount()) {
				long count = project.getPlans().stream().flatMap(p -> p.getAccountRequests() != null ? p.getAccountRequests().stream() : null).filter(req -> req.getOperation() != null && req.getOperation().equals(AccountRequest.Operation.Create)).count();
				if (count > 0) {
					throw new GeneralException("Provisioning request refused because it would result in a new account creation");
				}
			}
			if (isErrorOnAccountSelection() && (project.hasUnansweredAccountSelections() || project.hasUnansweredProvisioningTargets())) {
				throw new GeneralException("Provisioning request refused because we could not identify a target account");
			}
		}
		if (provisioningArguments.isUseWorkflow()) {
	        String planField = provisioningArguments.getPlanFieldName();
	        String userField = provisioningArguments.getIdentityFieldName();

	        Map<String, Object> workflowParameters = new HashMap<>();
	        workflowParameters.put(planField, plan);
	        workflowParameters.put(userField, plan.getIdentity().getName());

	        if (doRefresh) {
	            workflowParameters.put("doRefresh", true);
	        }

	        if (extraParameters != null) {
				extraParameters.putAll(workflowParameters);
	        }

	        Workflow wf = context.getObjectByName(Workflow.class, provisioningArguments.getWorkflowName());
	        Workflower workflower = new Workflower(context);
	        WorkflowLaunch launchOutput = workflower.launch(wf, getCaseName(plan.getIdentity().getName()), workflowParameters);

	        if (workflowDebugger != null) {
				workflowDebugger.accept(launchOutput);
			}

	        if (launchOutput != null && launchOutput.isFailed()) {
	        	throw new GeneralException("Workflow launch failed: " + launchOutput.getTaskResult().getMessages());
			}

			if (launchOutput != null && launchOutput.getTaskResult() != null) {
				ProvisioningPlan loggingPlan = ProvisioningPlan.getLoggingPlan(plan);
				if (loggingPlan != null) {
					launchOutput.getTaskResult().addMessage(Message.info(loggingPlan.toXml()));
				}

				String identityRequest = launchOutput.getTaskResult().getString("identityRequestId");
				if (Util.isNotNullOrEmpty(identityRequest)) {
					IdentityRequest ir = context.getObject(IdentityRequest.class, identityRequest);
					if (ir != null) {
						boolean save = false;
						if (Util.isNotNullOrEmpty(plan.getComments())) {
							ir.addMessage(Message.info(plan.getComments()));
							save = true;
						}
						if (Util.isNotNullOrEmpty(externalTicketId)) {
							ir.setExternalTicketId(externalTicketId);
							save = true;
						}
						if (save) {
							context.saveObject(ir);
							context.commitTransaction();
							context.decache(ir);
						}
						outputProject = ir.getProvisionedProject();
					}
				}
			}
		} else {
			Provisioner provisioner = new Provisioner(context);
			if (extraParameters != null) {
				extraParameters.forEach(provisioner::setArgument);
			}
			provisioner.setDoRefresh(doRefresh);
			provisioner.execute(plan);
			ProvisioningProject project = provisioner.getProject();
			// Do a refresh only if the project is fully committed; otherwise we might break something
			if (project.isFullyCommitted() && doRefresh) {
				Identity id = context.getObjectByName(Identity.class, plan.getIdentity().getName());
				new BaseIdentityUtilities(context).refresh(id, true);
			}
			outputProject = project;
		}
		return outputProject;
    }

	/**
	 * Enables the given account by submitting an Enable provisioning action to IIQ
	 * @param link The Link to enable
	 * @throws GeneralException if any IIQ errors occur
	 */
    public void enableAccount(Link link) throws GeneralException {
		Objects.requireNonNull(link, "Provided Link must not be null");
		ProvisioningPlan plan = new ProvisioningPlan();
		String username = ObjectUtil.getIdentityFromLink(context, link.getApplication(), link.getInstance(), link.getNativeIdentity());
		Identity user = findIdentity(username);
		plan.setIdentity(user);
		plan.add(link.getApplicationName(), link.getNativeIdentity(), AccountRequest.Operation.Enable);
		doProvisioning(plan);
	}

	/**
     * Finds the identity first by name and then by ID. This is mainly here so that
	 * it can be overridden by customer-specific subclasses. Otherwise, it does the
	 * same thing as {@link SailPointContext#getObject(Class, String)}.
	 *
     * @param identityName The identity name to search for
     * @return The Identity if found
     * @throws GeneralException if any errors occur
     */
	public Identity findIdentity(String identityName) throws GeneralException {
		Objects.requireNonNull(identityName);
        Identity output = context.getObjectByName(Identity.class, identityName);
        if (output != null) {
            return output;
        }
        output = context.getObjectById(Identity.class, identityName);
        return output;
    }

	/**
	 * Force retry on the given transaction. There is an out-of-box API for doing this
	 * on transactions pending retry (to force them to run 'right now' rather than 'later'),
	 * but there is none for doing this on failed transactions.
	 *
	 * @param pt The transaction to retry
	 * @param createToModify If true, a Create operation will be transmuted to a Modify
	 * @throws GeneralException if any failures occur
	 */
	public ProvisioningProject forceRetry(ProvisioningTransaction pt, boolean createToModify) throws GeneralException {
		Objects.requireNonNull(pt, "Provided ProvisioningTransaction must not be null");
		if (pt.getStatus().equals(ProvisioningTransaction.Status.Failed)) {
			AccountRequest request = (AccountRequest)pt.getAttributes().get("request");
			if (request != null) {
				AccountRequest cloned = (AccountRequest)request.cloneRequest();
				ProvisioningResult originalResult = request.getResult();
				if (originalResult != null && originalResult.getStatus() != null) {
					ProvisioningResult newResult = new ProvisioningResult();
					newResult.setWarnings(originalResult.getErrors());
					newResult.setStatus(ProvisioningResult.STATUS_RETRY);
					cloned.setResult(newResult);
				}
				if (createToModify && cloned.getOperation() != null && cloned.getOperation().equals(AccountRequest.Operation.Create)) {
					cloned.setOperation(AccountRequest.Operation.Modify);
				}
				Attributes<String, Object> arguments = getArguments(cloned);
				arguments.put("provisioningTransactionId", pt.getId());
				cloned.setArguments(arguments);
				ProvisioningPlan retryPlan = new ProvisioningPlan();
				retryPlan.add(cloned);
				retryPlan.setIdentity(context.getObject(Identity.class, pt.getIdentityName()));
				retryPlan.setTargetIntegration(pt.getIntegration());
				retryPlan.setComments("Retry for Provisioning Transaction ID " + pt.getId());
				setUseWorkflow(false);
				return doProvisioning(retryPlan.getIdentity().getName(), retryPlan, false, new HashMap<>());
			} else {
				throw new IllegalArgumentException("You can only forceRetry on transaction with an AccountRequest attached");
			}
		} else {
			throw new IllegalArgumentException("You can only forceRetry on failed transactions; IIQ will redo 'retry' transactions on its own");
		}
	}

	/**
	 * Builds the case name based on the template provided
	 * @param identityName The identity name passed to this case
	 * @return The case name generated
	 */
	public String getCaseName(String identityName) {
		String caseName = provisioningArguments.getCaseNameTemplate().replace("{Workflow}", provisioningArguments.getWorkflowName());
		caseName = caseName.replace("{IdentityName}", identityName);
		caseName = caseName.replace("{Timestamp}", String.valueOf(System.currentTimeMillis()));
		if (caseName.contains("{Date}")) {
			DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
			Instant now = Instant.now();
			caseName = caseName.replace("{Date}", formatter.format(now));
		}

		return caseName;
	}

	public String getCaseNameTemplate() {
		return provisioningArguments.getCaseNameTemplate();
	}
	
	public String getExternalTicketId() {
		return externalTicketId;
	}

	public String getProvisioningWorkflow() {
		return provisioningArguments.getWorkflowName();
	}

	public boolean isErrorOnAccountSelection() {
		return provisioningArguments.isErrorOnAccountSelection();
	}

	public boolean isErrorOnManualTask() {
		return provisioningArguments.isErrorOnManualTask();
	}

	public boolean isErrorOnNewAccount() {
		return provisioningArguments.isErrorOnNewAccount();
	}

	public boolean isErrorOnProvisioningForms() {
		return provisioningArguments.isErrorOnProvisioningForms();
	}

	public boolean isUseWorkflow() {
		return provisioningArguments.isUseWorkflow();
	}

	/**
	 * Moves the given target account(s) to the given target owner
	 * @param targetOwner The target owner for the given accounts
	 * @param accounts One or more accounts to move to the new owner
	 * @throws GeneralException if anything goes wrong during provisioning
	 */
	public void moveLinks(Identity targetOwner, Link... accounts) throws GeneralException {
		if (accounts != null && accounts.length > 0) {
			ProvisioningPlan plan = new ProvisioningPlan();
			for (Link account : accounts) {
				linkMovePlan(account, targetOwner, plan);
			}
			doProvisioning(plan);
		}
	}

	/**
	 * Removes all entitlements and assigned roles from the given Identity
	 *
	 * @param identity The identity from whom to strip access
	 * @throws GeneralException if anything goes wrong during provisioning
	 */
	public void removeAllAccess(Identity identity) throws GeneralException {
		ProvisioningPlan entitlementPlan = new ProvisioningPlan();
		entitlementPlan.setIdentity(identity);

		for(Link account : Util.safeIterable(identity.getLinks())) {
			List<String> entitlementAttributes = account.getApplication().getEntitlementAttributeNames();
			for(String attribute : Util.safeIterable(entitlementAttributes)) {
				removeAllEntitlements(account, attribute, entitlementPlan);
			}
		}

		Map<String, Object> options = new HashMap<>();
		options.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);

		doProvisioning(identity.getName(), entitlementPlan, false, options);

		ObjectUtil.saveDecacheAttach(context, identity);

		ProvisioningPlan rolePlan = new ProvisioningPlan();
		rolePlan.setIdentity(identity);

		for(Bundle role : Util.safeIterable(identity.getAssignedRoles())) {
			removeUserRolePlan(role.getName(), true, rolePlan);
		}

		doProvisioning(identity.getName(), rolePlan, false, options);

		ObjectUtil.saveDecacheAttach(context, identity);
	}
    
	/**
	 * Removes all entitlements from all accounts of the given type on the given user
	 * @param identity The identity to target
	 * @param target The target application from which to remove accounts
	 * @throws GeneralException if a failure occurs
	 */
    public void removeAllEntitlements(Identity identity, Application target) throws GeneralException {
    	ProvisioningPlan plan = new ProvisioningPlan();
    	plan.setIdentity(identity);
		removeAllEntitlements(identity, target, plan);
		Map<String, Object> options = new HashMap<>();
		options.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);

		doProvisioning(identity.getName(), plan, false, options);
	}

	/**
	 * Modifies the plan to add entitlement removal requests for all entitlements on
	 * accounts of the given type
	 * @param identity The identity from which to extract the entitlements
	 * @param target The target application
	 * @param plan The provisioning plan
	 * @throws GeneralException if any failures occur
	 */
	public void removeAllEntitlements(Identity identity, Application target, ProvisioningPlan plan) throws GeneralException {
		IdentityService identityService = new IdentityService(context);
		List<Link> links = identityService.getLinks(identity, target);
		List<String> entitlementAttributes = target.getEntitlementAttributeNames();
		for(Link account : Util.safeIterable(links)) {
			for(String attribute : Util.safeIterable(entitlementAttributes)) {
				removeAllEntitlements(account, attribute, plan);
			}
		}
	}

	/**
	 * Modifies the plan to remove all values from the given attribute on the given
	 * account.
	 * @param account The account to modify
	 * @param attribute The attribute to remove attributes from
	 * @param plan The provisioning plan
	 */
	@SuppressWarnings("unchecked")
	public void removeAllEntitlements(Link account, String attribute, ProvisioningPlan plan) {
		if (account == null || account.getAttributes() == null) {
			return;
		}
		List<String> values = account.getAttributes().getList(attribute);
		if (values != null && !values.isEmpty()) {
			AccountRequest accountRequest = plan.getAccountRequest(account.getApplicationName(), null, account.getNativeIdentity());
			if (accountRequest == null) {
				accountRequest = new AccountRequest(AccountRequest.Operation.Modify, account.getApplicationName(), null, account.getNativeIdentity());
				plan.add(accountRequest);
			}
			AttributeRequest attributeRequest = new AttributeRequest(attribute, ProvisioningPlan.Operation.Remove, values);
			attributeRequest.setAssignment(true);
			accountRequest.add(attributeRequest);
		}
	}

	/**
	 * Removes the given entitlement from the given account on the user
	 * @param identityName The identity name
	 * @param account The account to modify
	 * @param entitlement The managed attribute from which to extract the entitlement
	 * @param withApproval If false, approval will be skipped
	 * @throws GeneralException if any failure occurs
	 */
	public void removeEntitlement(String identityName, Link account, ManagedAttribute entitlement, boolean withApproval) throws GeneralException {
		ProvisioningPlan plan = new ProvisioningPlan();
		Identity identity = findIdentity(identityName);
		plan.setIdentity(identity);

		AccountRequest accountRequest = new AccountRequest(AccountRequest.Operation.Modify, account.getApplicationName(), account.getInstance(), account.getNativeIdentity());
		plan.add(accountRequest);

		AttributeRequest attributeRequest = new AttributeRequest(entitlement.getName(), ProvisioningPlan.Operation.Remove, entitlement.getValue());
		attributeRequest.setAssignment(true);
		accountRequest.add(attributeRequest);

		Map<String, Object> options = new HashMap<>();
		if (!withApproval) {
			options.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		}

		doProvisioning(identityName, plan, false, options);
	}
	
	/**
	 * Removes the given user from the given role
	 * @param targetAssignment The target existing RoleAssignment from an Identity
	 * @throws GeneralException if a provisioning failure occurs
	 */
	public void removeUserRole(String identityName, RoleAssignment targetAssignment) throws GeneralException {
		Objects.requireNonNull(identityName, "A non-null Identity name is required");
		Objects.requireNonNull(targetAssignment, "The provided RoleAssignment must not be null");
		if (targetAssignment.isNegative()) {
			throw new IllegalArgumentException("Cannot remove a negative assignment using this API");
		}
		ProvisioningPlan.Operation roleOp = ProvisioningPlan.Operation.Remove;
		if (Util.nullSafeEq(targetAssignment.getSource(), "Rule")) {
			roleOp = ProvisioningPlan.Operation.Revoke;
		}
		Identity identity = findIdentity(identityName);
		ProvisioningPlan provisioningPlan = new ProvisioningPlan();
		provisioningPlan.setIdentity(identity);
		AccountRequest accountRequest = new AccountRequest(AccountRequest.Operation.Modify, ProvisioningPlan.APP_IIQ, null, identity.getName());
		AttributeRequest attributeRequest = new AttributeRequest(ASSIGNED_ROLES_ATTR, roleOp, targetAssignment.getRoleName());
		attributeRequest.setAssignmentId(targetAssignment.getAssignmentId());
		accountRequest.add(attributeRequest);
		provisioningPlan.add(accountRequest);
		Map<String, Object> options = new HashMap<>();
		options.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		doProvisioning(identity.getName(), provisioningPlan, false, options);
	}

	/**
	 * Removes the given user from the given role. For a detected role, this will
	 * remove any entitlements provisioned by that role that are not required by
	 * another role assigned to the user.
	 *
	 * @param identityName The name of the Identity to modify
	 * @param targetDetection The target existing RoleDetection from an Identity
	 * @throws GeneralException if a provisioning failure occurs
	 */
	public void removeUserRole(String identityName, RoleDetection targetDetection) throws GeneralException {
		Objects.requireNonNull(identityName, "A non-null Identity name is required");
		Objects.requireNonNull(targetDetection, "The provided RoleDetection must not be null");
		if (targetDetection.hasAssignmentIds()) {
			throw new IllegalArgumentException("Cannot remove a required detected role using this API");
		}
		String tempAssignmentKey = "TEMP:" + Util.uuid();
		Identity identity = findIdentity(identityName);
		ProvisioningPlan provisioningPlan = new ProvisioningPlan();
		provisioningPlan.setIdentity(identity);
		AccountRequest accountRequest = new AccountRequest(AccountRequest.Operation.Modify, ProvisioningPlan.APP_IIQ, null, identity.getName());
		AttributeRequest attributeRequest = new AttributeRequest("detectedRoles", ProvisioningPlan.Operation.Remove, targetDetection.getRoleName());

		BaseIdentityUtilities identityUtilities = new BaseIdentityUtilities(context);
		if (identityUtilities.hasMultiple(identity, targetDetection.getRoleName())) {
			ProvisioningTarget target = new ProvisioningTarget();
			target.setAssignmentId(tempAssignmentKey);
			target.setRole(targetDetection.getRoleName());
			Utilities.safeStream(targetDetection.getTargets()).map(ProvisioningUtilities::roleTargetToAccountSelection).forEach(target::addAccountSelection);
			if (Util.nullSafeSize(target.getAccountSelections()) > 0) {
				List<ProvisioningTarget> targets = provisioningPlan.getProvisioningTargets();
				if (targets == null) {
					targets = new ArrayList<>();
				}
				targets.add(target);
				provisioningPlan.setProvisioningTargets(targets);
				attributeRequest.setAssignmentId(tempAssignmentKey);
			}
		}
		accountRequest.add(attributeRequest);
		provisioningPlan.add(accountRequest);
		Map<String, Object> options = new HashMap<>();
		options.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		doProvisioning(identity.getName(), provisioningPlan, false, options);
	}
	
	/**
	 * Removes the given user from the given role associated with the target provisioned account.
	 * Note that the role may also be associated with a different account. This is used only to
	 * locate the RoleAssignment object for deprovisioning by assignment ID.
	 * @param identityName The identity name to add to the given role
	 * @param roleName The role to add
	 * @param withApproval If true, default approval will be required
	 * @param target If not null, this will be used as a provisioning target for the plan
	 * @throws GeneralException if a provisioning failure occurs
	 */
	public void removeUserRole(String identityName, String roleName, boolean withApproval, Link target) throws GeneralException {
		// We have to generate our own assignment ID in this case
		Bundle role = context.getObjectByName(Bundle.class, roleName);
		if (role == null) {
			throw new IllegalArgumentException("Role " + roleName + " does not exist");
		}
		Identity identity = findIdentity(identityName);
		List<RoleAssignment> existingAssignments = identity.getRoleAssignments(role);
		RoleAssignment targetAssignment = null;
		for(RoleAssignment ra : existingAssignments) {
			RoleTarget rt = new RoleTarget(target);
			if (ra.hasMatchingRoleTarget(rt)) {
				targetAssignment = ra;
				break;
			}
		}
		if (targetAssignment == null) {
			throw new IllegalArgumentException("The user " + identityName + " does not have a role " + roleName + " targeting " + target.getApplicationName() + " account " + target.getNativeIdentity());
		}
		ProvisioningPlan provisioningPlan = new ProvisioningPlan();
		provisioningPlan.setIdentity(identity);
		AccountRequest accountRequest = new AccountRequest(AccountRequest.Operation.Modify, ProvisioningPlan.APP_IIQ, null, identity.getName());
		AttributeRequest attributeRequest = new AttributeRequest(ASSIGNED_ROLES_ATTR, ProvisioningPlan.Operation.Remove, roleName);
		attributeRequest.setAssignmentId(targetAssignment.getAssignmentId());
		accountRequest.add(attributeRequest);
		provisioningPlan.add(accountRequest);
		Map<String, Object> options = new HashMap<>();
		if (!withApproval) {
			options.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		}
		doProvisioning(provisioningPlan.getIdentity().getName(), provisioningPlan, false, options);
	}

	/**
	 * Removes the given role from the given user
	 * @param identityName The identity to remove the role from
	 * @param roleName The role to remove from the identity
	 * @throws GeneralException If a failure occurs
	 */
	public void removeUserRole(String identityName, String roleName) throws GeneralException {
		removeUserRole(identityName, roleName, false, false);
	}

	/**
	 * Removes the given role from the given user
	 * @param identityName The identity to remove the role from
	 * @param roleName The role to remove from the identity
	 * @param withApproval If true, a default approval will be required
	 * @throws GeneralException If a failure occurs
	 */
	public void removeUserRole(String identityName, String roleName, boolean withApproval) throws GeneralException {
		removeUserRole(identityName, roleName, withApproval, false);
	}

	/**
	 * Removes the given role from the given user
	 * @param identityName The identity to remove the role from
	 * @param roleName The role to remove from the identity
	 * @param withApproval If true, a default approval will be required
	 * @param revoke If true, the role will be revoked and not removed
	 * @throws GeneralException If a failure occurs
	 */
    public void removeUserRole(String identityName, String roleName, boolean withApproval, boolean revoke) throws GeneralException {
        ProvisioningPlan provisioningPlan = new ProvisioningPlan();
        Identity identity = findIdentity(identityName);
        provisioningPlan.setIdentity(identity);
		removeUserRolePlan(roleName, revoke, provisioningPlan);
        Map<String, Object> options = new HashMap<>();
        if (!withApproval) {
            options.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
        }
        doProvisioning(identity.getName(), provisioningPlan, false, options);
    }

	public void setBeforeProvisioning(Consumer<ProvisioningPlan> planConsumer) {
		this.beforeProvisioningConsumer = planConsumer;
	}

	public void setCaseNameTemplate(String caseNameTemplate) {
		provisioningArguments.setCaseNameTemplate(caseNameTemplate);
	}

	public void setErrorOnAccountSelection(boolean errorOnAccountSelection) {
		provisioningArguments.setErrorOnAccountSelection(errorOnAccountSelection);
	}

	public void setErrorOnManualTask(boolean errorOnManualTask) {
		provisioningArguments.setErrorOnManualTask(errorOnManualTask);
	}

	public void setErrorOnNewAccount(boolean errorOnNewAccount) {
		provisioningArguments.setErrorOnNewAccount(errorOnNewAccount);
	}

	public void setErrorOnProvisioningForms(boolean errorOnProvisioningForms) {
		provisioningArguments.setErrorOnProvisioningForms(errorOnProvisioningForms);
	}

	public void setExternalTicketId(String externalTicketId) {
		this.externalTicketId = externalTicketId;
	}
	
	public void setProjectDebugger(Consumer<ProvisioningProject> projectDebugger) {
		this.projectDebugger = projectDebugger;
	}

	/**
	 * Sets workflow configuration items all at once for this utility
	 * @param config The workflow configuration to use
	 */
	public void setProvisioningArguments(ProvisioningArguments config) {
		this.provisioningArguments.merge(config);
	}
	
	public void setProvisioningWorkflow(String provisioningWorkflow) {
		provisioningArguments.setWorkflowName(provisioningWorkflow);
	}

	public void setUseWorkflow(boolean useWorkflow) {
		provisioningArguments.setUseWorkflow(useWorkflow);
	}

	public void setWorkflowDebugger(Consumer<WorkflowLaunch> workflowDebugger) {
		this.workflowDebugger = workflowDebugger;
	}

	/**
	 * Transforms this object into a Map that can be passed to the constructor
	 * that takes a Map
	 *
	 * @return The resulting map transformation
	 */
	public Attributes<String, Object> toMap() {
		Attributes<String, Object> map = new Attributes<>();
		map.put("caseNameTemplate", this.provisioningArguments.getCaseNameTemplate());
		map.put("defaultExtraParameters", this.provisioningArguments.getDefaultExtraParameters());
		map.put("errorOnAccountSelection", this.provisioningArguments.isErrorOnAccountSelection());
		map.put("errorOnManualTask", this.provisioningArguments.isErrorOnManualTask());
		map.put("errorOnNewAccount", this.provisioningArguments.isErrorOnNewAccount());
		map.put("errorOnProvisioningForms", this.provisioningArguments.isErrorOnProvisioningForms());
		map.put("externalTicketId", this.externalTicketId);
		map.put("planArguments", this.planArguments);
		map.put("workflowPlanField", this.provisioningArguments.getPlanFieldName());
		map.put("workflowIdentityField", this.provisioningArguments.getIdentityFieldName());
		map.put("provisioningWorkflow", this.provisioningArguments.getWorkflowName());
		map.put("useWorkflow", this.provisioningArguments.isUseWorkflow());
		return map;
	}

	/**
	 * Updates the given link with the given values. Field names can also have the form "Operation:Name", e.g. "Add:memberOf", to specify an operation.
	 *
	 * Values 'Set' to a multi-value field will be transformed to 'Add' by default. You can override this using the colon syntax above, which will always take priority.
	 *
	 * @param link The Link to update
	 * @param map The values to update (Set by default)
	 * @throws GeneralException if any provisioning failures occur
	 */
	public void updateAccount(Link link, Map<String, Object> map) throws GeneralException {
		Objects.requireNonNull(link, "The provided Link must not be null");
		if (map == null || map.isEmpty()) {
			log.warn("Call made to updateAccount() with a null or empty map of updates");
			return;
		}
		ProvisioningPlan plan = new ProvisioningPlan();
		String username = ObjectUtil.getIdentityFromLink(context, link.getApplication(), link.getInstance(), link.getNativeIdentity());
		Identity user = findIdentity(username);

		plan.setIdentity(user);

		for(String key : map.keySet()) {
			String provisioningName = key;
			ProvisioningPlan.Operation operation = ProvisioningPlan.Operation.Set;
			if (key.contains(":")) {
				String[] components = key.split(":");
				operation = ProvisioningPlan.Operation.valueOf(components[0]);
				provisioningName = components[1];
			}
			// For multi-valued attributes, transform set to add by default
			if (operation.equals(ProvisioningPlan.Operation.Set) && !key.contains(":")) {
				AttributeDefinition attributeDefinition = link.getApplication().getAccountSchema().getAttributeDefinition(provisioningName);
				if (attributeDefinition.isMultiValued()) {
					operation = ProvisioningPlan.Operation.Add;
				}
			}
			AccountRequest request = plan.add(link.getApplicationName(), link.getNativeIdentity(), provisioningName, operation, map.get(key));
			if (request.getOperation() == null) {
				request.setOperation(AccountRequest.Operation.Modify);
			}
		}

		Map<String, Object> extraParameters = new HashMap<>();
		extraParameters.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		doProvisioning(username, plan, false, extraParameters);
	}

	/**
	 * Updates the given link by setting or adding the given values. Multi-value attributes will be transformed to Set.
	 *
	 * @param link The Link to update
	 * @param attribute The name of the attribute to either set or add
	 * @param value The value(s) to set or add
	 * @throws GeneralException if any provisioning failures occur
	 */
	public void updateAccountRemove(Link link, String attribute, Object value) throws GeneralException {
		Objects.requireNonNull(link, "The provided Link must not be null");
		ProvisioningPlan plan = new ProvisioningPlan();
		String username = ObjectUtil.getIdentityFromLink(context, link.getApplication(), link.getInstance(), link.getNativeIdentity());
		Identity user = findIdentity(username);

		plan.setIdentity(user);

		AccountRequest request = plan.add(link.getApplicationName(), link.getNativeIdentity(), attribute, ProvisioningPlan.Operation.Remove, value);
		if (request.getOperation() == null) {
			request.setOperation(AccountRequest.Operation.Modify);
		}

		Map<String, Object> extraParameters = new HashMap<>();
		extraParameters.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		doProvisioning(username, plan, false, extraParameters);
	}

	/**
	 * Updates the given link by setting or adding the given values. Multi-value attributes will be transformed to Set.
	 *
	 * @param link The Link to update
	 * @param attribute The name of the attribute to either set or add
	 * @param value The value(s) to set or add
	 * @throws GeneralException if any provisioning failures occur
	 */
	public void updateAccountSet(Link link, String attribute, Object value) throws GeneralException {
		Objects.requireNonNull(link, "The provided Link must not be null");
		ProvisioningPlan plan = new ProvisioningPlan();
		String username = ObjectUtil.getIdentityFromLink(context, link.getApplication(), link.getInstance(), link.getNativeIdentity());
		Identity user = findIdentity(username);

		plan.setIdentity(user);

		ProvisioningPlan.Operation operation = ProvisioningPlan.Operation.Set;
		// For multi-valued attributes, transform set to add
		AttributeDefinition attributeDefinition = link.getApplication().getAccountSchema().getAttributeDefinition(attribute);
		if (attributeDefinition != null && attributeDefinition.isMultiValued()) {
			operation = ProvisioningPlan.Operation.Add;
		}
		AccountRequest request = plan.add(link.getApplicationName(), link.getNativeIdentity(), attribute, operation, value);
		if (request.getOperation() == null) {
			request.setOperation(AccountRequest.Operation.Modify);
		}
		Map<String, Object> extraParameters = new HashMap<>();
		extraParameters.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		doProvisioning(username, plan, false, extraParameters);
	}

	/**
	 * Updates the given identity with the given values. Field names can also have the form "Operation:Name", e.g. "Add:memberOf", to specify an operation.
	 *
	 * Values 'Set' to a multi-value field will be transformed to 'Add' by default. You can override this using the colon syntax above, which will always take priority.
	 *
	 * @param identity The identity to modify
	 * @param params The parameters to modify
	 * @throws GeneralException if anything goes wrong
	 */
	public void updateUser(Identity identity, Map<String, Object> params) throws GeneralException {
		updateUser(identity, "Set", params);
	}

	/**
	 * Updates the given identity with the given values. Field names can also have the form "Operation:Name", e.g. "Add:memberOf", to specify an operation.
	 *
	 * Values 'Set' to a multi-value field will be transformed to 'Add' by default. You can override this using the colon syntax above, which will always take priority.
	 *
	 * @param identity The identity to modify
	 * @param defaultOperation The default operation to update with (Set, Add, Remove, etc) if one is not given
	 * @param params The parameters to modify
	 * @throws GeneralException if anything goes wrong
	 */
	public void updateUser(Identity identity, String defaultOperation, Map<String, Object> params) throws GeneralException {
		if (params == null || params.isEmpty()) {
			log.warn("Call made to updateAccount() with a null or empty map of updates");
			return;
		}
		Objects.requireNonNull(identity, "Identity must not be null");
		ProvisioningPlan plan = new ProvisioningPlan();
		plan.setIdentity(identity);
		for(String key : params.keySet()) {
			String provisioningName = key;
			ProvisioningPlan.Operation operation = ProvisioningPlan.Operation.valueOf(defaultOperation);
			if (key.contains(":")) {
				String[] components = key.split(":");
				operation = ProvisioningPlan.Operation.valueOf(components[0]);
				provisioningName = components[1];
			}
			// For multi-valued attributes, transform set to add by default
			if (operation.equals(ProvisioningPlan.Operation.Set) && !key.contains(":")) {
				ObjectConfig identityConfig = Identity.getObjectConfig();
				ObjectAttribute attributeDefinition = identityConfig.getObjectAttribute(provisioningName);
				if (attributeDefinition.isMultiValued()) {
					operation = ProvisioningPlan.Operation.Add;
				}
			}
			Object value = params.get(key);
			if (value instanceof Identity) {
				value = ((Identity) value).getName();
			}
			plan.add(ProvisioningPlan.APP_IIQ, identity.getName(), provisioningName, operation, value);
		}
		Map<String, Object> extraParameters = new HashMap<>();
		extraParameters.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		doProvisioning(identity.getName(), plan, false, extraParameters);
	}

	/**
	 * Updates the given user with the given field values
	 * @param identity The identity in question
	 * @param field The field to set
	 * @param operation The operation to use
	 * @param value The value to update
	 * @throws GeneralException if any provisioning failures occur
	 */
	public void updateUser(Identity identity, String field, ProvisioningPlan.Operation operation, Object value) throws GeneralException {
		ProvisioningPlan changes = new ProvisioningPlan();
		if (value instanceof Identity) {
			value = ((Identity) value).getName();
		}
		changes.add(ProvisioningPlan.APP_IIQ, identity.getName(), field, operation, value);
		changes.setIdentity(identity);
		Map<String, Object> extraParameters = new HashMap<>();
		extraParameters.put(PLAN_PARAM_APPROVAL_SCHEME, NO_APPROVAL_SCHEME);
		doProvisioning(identity.getName(), changes, false, extraParameters);
	}

}
