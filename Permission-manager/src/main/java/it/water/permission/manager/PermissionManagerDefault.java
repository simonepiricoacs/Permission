/*
 * Copyright 2024 Aristide Cittadino
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.water.permission.manager;

import it.water.core.api.action.Action;
import it.water.core.api.action.ActionsManager;
import it.water.core.api.action.ResourceAction;
import it.water.core.api.bundle.Runtime;
import it.water.core.api.entity.owned.OwnedChildResource;
import it.water.core.api.entity.owned.OwnedResource;
import it.water.core.api.entity.shared.SharedEntity;
import it.water.core.api.entity.tenant.MultiTenantResource;
import it.water.core.api.entity.tenant.TenantResource;
import it.water.core.api.model.BaseEntity;
import it.water.core.api.model.Resource;
import it.water.core.api.model.Role;
import it.water.core.api.model.User;
import it.water.core.api.permission.Permission;
import it.water.core.api.permission.PermissionManager;
import it.water.core.api.permission.PermissionManagerComponentProperties;
import it.water.core.api.permission.ProtectedEntity;
import it.water.core.api.permission.SecurityContext;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.service.BaseEntitySystemApi;
import it.water.core.api.service.integration.PermissionIntegrationClient;
import it.water.core.api.service.integration.RoleIntegrationClient;
import it.water.core.api.service.integration.SharedEntityIntegrationClient;
import it.water.core.api.service.integration.TenantMembershipResolver;
import it.water.core.api.service.integration.UserIntegrationClient;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.permission.action.ActionFactory;
import it.water.core.permission.action.UserActions;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@FrameworkComponent(properties = {PermissionManagerComponentProperties.PERMISSION_MANAGER_IMPLEMENTATION_PROP + "=" + PermissionManagerComponentProperties.PERMISSION_MANAGER_DEFAILT_IMPLEMENTATION})
public class PermissionManagerDefault implements PermissionManager {
    private Logger log = LoggerFactory.getLogger(PermissionManagerDefault.class.getName());

    //supporting spring properties bind with bean properties
    @Setter
    @Getter
    private String implementation = PermissionManagerComponentProperties.PERMISSION_MANAGER_DEFAILT_IMPLEMENTATION;
    /**
     * Injecting the PermissionSystemService to use methods in PermissionSystemApi
     * interface
     */
    @Inject
    @Setter
    private PermissionIntegrationClient permissionIntegrationClient;

    @Inject
    @Setter
    private UserIntegrationClient userIntegrationClient;

    @Inject
    @Setter
    private SharedEntityIntegrationClient sharedEntityIntegrationClient;

    @Inject
    @Setter
    private RoleIntegrationClient roleIntegrationClient;

    @Inject
    @Setter
    private ActionsManager actionsManager;

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    /**
     * @param username
     * @param rolesNames
     * @return
     */
    @Override
    public boolean userHasRoles(String username, String[] rolesNames) {
        if (username == null || username.isEmpty())
            return false;
        Collection<String> rolesNamesCollection = Arrays.asList(rolesNames);
        User u = userIntegrationClient.fetchUserByUsername(username);
        Collection<Role> roles = roleIntegrationClient.fetchUserRoles(u.getId());
        //find user Roles
        return roles.stream().anyMatch(r -> rolesNamesCollection.contains(r.getName()));
    }

    /**
     * Checks if an existing user has permissions for action of HyperIoTAction.
     * Moreover every user, if protected, is set as a base entity of the HyperIoT
     * platform.
     *
     * @param username parameter that indicates the username of user
     * @param entity   parameter that indicates the resource name of user
     * @param action   interaction of the user with HyperIoT platform
     */
    @Override
    public boolean checkPermission(String username, Resource entity,
                                   Action action) {
        log.debug(
                "invoking checkPermission User {} Entity Resource Name: {}", username, entity);

        if (entity != null && !PermissionManager.isProtectedEntity(entity.getResourceName()))
            return true;

        if (entity != null && !PermissionManager.isProtectedEntity(entity))
            return true;

        if (username == null || entity == null || action == null)
            return false;

        User user = this.userIntegrationClient.fetchUserByUsername(username);
        if (user == null)
            return false;
        // every protected entity is a base entity
        ProtectedEntity entityResource = (ProtectedEntity) entity;

        return hasPermission(user, entityResource, action);
    }

    /**
     * Checks if an existing user has permissions for action of HyperIoTAction.
     *
     * @param username     parameter that indicates the username of user
     * @param resourceName parameter that indicates the resource name of action
     * @param action       interaction of the user with HyperIoT platform
     */
    @Override
    public boolean checkPermission(String username, String resourceName, Action action) {
        log.debug("invoking checkPermission User {} Entity Resource Name: {} Action: {} ", username, resourceName, action);
        if (username == null || resourceName == null || action == null)
            return false;

        if (!PermissionManager.isProtectedEntity(resourceName))
            return true;

        return hasPermission(username, resourceName, action);
    }

    /**
     * Checks if an existing user has permissions for action of HyperIoTAction.
     *
     * @param username parameter that indicates the username of user
     * @param resource parameter that indicates the resource name of action
     * @param action   interaction of the user with HyperIoT platform
     */
    @Override
    public boolean checkPermission(String username, Class<? extends Resource> resource,
                                   Action action) {
        if (username == null || resource == null || action == null)
            return false;

        if (!PermissionManager.isProtectedEntity(resource.getName()))
            return true;

        log.debug(
                "invoking checkPermission User {} Entity Resource Name: {} Action Name: {}  actionId: {}", username, resource.getName(), action.getActionName(), action.getActionId());
        return hasPermission(username, resource.getName(), action);
    }

    /**
     * Returns a map containing all actiona available for every resource
     *
     * @param username
     * @param entityPks
     * @return
     */
    @Override
    public Map<String, Map<String, Map<String, Boolean>>> entityPermissionMap(String username, Map<String, List<Long>> entityPks) {
        Map<String, Map<String, Map<String, Boolean>>> userPermissionMap = new HashMap<>();
        entityPks.keySet().forEach(entityClass -> {
            userPermissionMap.computeIfAbsent(entityClass, key -> new HashMap<>());
            BaseEntitySystemApi<?> baseEntitySystemApi = componentRegistry.findEntitySystemApi(entityClass);
            if (baseEntitySystemApi != null) {
                entityPks.get(entityClass).forEach(entityId -> {
                    String entityIdsSts = String.valueOf(entityId);
                    userPermissionMap.get(entityClass).computeIfAbsent(entityIdsSts, key -> new HashMap<>());
                    try {
                        BaseEntity entity = baseEntitySystemApi.find(entityId);
                        if (entity != null) {
                            List<ResourceAction<Resource>> actions = actionsManager.getActions().get(entityClass).getList();
                            actions.forEach(resourceAction -> {
                                boolean hasPermission = checkPermission(username, entity, resourceAction.getAction());
                                userPermissionMap.get(entityClass).get(entityIdsSts).put(resourceAction.getAction().getActionName(), hasPermission);
                            });
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                });
            }
        });
        return userPermissionMap;
    }

    /**
     * @param username     parameter that indicates the username of entity
     * @param resourceName parameter that indicates the resource name of action
     * @param action       interaction of the user with HyperIoT platform
     * @param entities     List of entities User must own in order to perform the action
     * @return
     */
    public boolean checkPermissionAndOwnership(String username, String resourceName, Action action, Resource... entities) {
        boolean hasPermission = false;
        if (!PermissionManager.isProtectedEntity(resourceName))
            hasPermission = true;
        else
            hasPermission = checkPermission(username, resourceName, action);

        if (hasPermission && entities != null) {
            User user = this.userIntegrationClient.fetchUserByUsername(username);
            for (int i = 0; i < entities.length && hasPermission; i++) {
                hasPermission = user != null && entities[i] != null && checkUserOwnsResource(user, entities[i]);
            }
        }
        return hasPermission;
    }

    /**
     * @param username parameter that indicates the username of entity
     * @param resource parameter that indicates the resource on which the action should be performed
     * @param action   interaction of the user with HyperIoT platform
     * @param entities List of other entities User must own in order to perform the action
     * @return
     */
    public boolean checkPermissionAndOwnership(String username, Resource resource, Action action, Resource... entities) {
        boolean hasPermission = false;
        if (!PermissionManager.isProtectedEntity(resource.getResourceName()))
            hasPermission = true;
        else
            hasPermission = checkPermission(username, resource.getResourceName(), action);

        if (hasPermission && entities != null) {
            User user = this.userIntegrationClient.fetchUserByUsername(username);
            for (int i = 0; i < entities.length && hasPermission; i++) {
                hasPermission = user != null && entities[i] != null && checkUserOwnsResource(user, entities[i]);
            }
        }
        return hasPermission;
    }


    /**
     * Find an existing user by username. Returns actions permission by user role.
     *
     * @param username     parameter required to find a user by his username
     * @param resourceName parameter that indicates the resource name
     * @param action       interaction of the user with HyperIoT platform
     * @return Actions permission by user
     */
    private boolean hasPermission(String username, String resourceName, Action action) {
        User user = this.userIntegrationClient.fetchUserByUsername(username);
        if (user == null) {
            return false;
        }

        if (user.isAdmin())
            return true;

        Collection<Role> userRoles = roleIntegrationClient.fetchUserRoles(user.getId());

        if (userRoles.isEmpty())
            return false;

        Iterator<? extends Role> it = userRoles.iterator();

        while (it.hasNext()) {
            Role r = it.next();
            Permission permission = permissionIntegrationClient.findByRoleAndResourceName(r.getId(), resourceName);
            if (permission != null
                    && hasPermission(permission.getActionIds(), action.getActionId()))
                return true;
        }
        return false;
    }

    /**
     * Find an existing user by username. Returns actions permission by user role.
     *
     * @param user   parameter required to find a user by his username
     * @param action interaction of the user with HyperIoT platform
     * @return Actions permission by user
     */
    private boolean hasPermission(User user, ProtectedEntity entity,
                                  Action action) {
        if (user.isAdmin())
            return true;

        Collection<Role> userRoles = roleIntegrationClient.fetchUserRoles(user.getId());

        if (userRoles.isEmpty())
            return false;

        Iterator<? extends Role> it = userRoles.iterator();
        boolean hasPermission = false;
        while (it.hasNext()) {
            Role r = it.next();
            Permission permissionSpecific = permissionIntegrationClient.findByRoleAndResourceNameAndResourceId(r.getId(),
                    entity.getResourceName(), entity.getId());
            Permission userPermissionSpecific = permissionIntegrationClient.findByUserAndResourceNameAndResourceId(user.getId(),
                    entity.getResourceName(), entity.getId());
            Permission permissionImpersonation = permissionIntegrationClient.findByRoleAndResourceName(r.getId(),
                    User.class.getName());
            // it initialize the value with the general value based on resource name
            // general permission is : permission based on the role or permission based on user
            boolean hasGeneralPermission = hasGeneralPermission(user, r, entity, action);
            // entity permission is specific if it is found on role or user
            boolean hasEntityPermission = hasEntityPermission(permissionSpecific, action, userPermissionSpecific);
            boolean existPermissionSpecificToEntity = permissionIntegrationClient.permissionSpecificToEntityExists(entity.getResourceName(), entity.getId());
            boolean userActionsAreRegistered = actionsManager.getActions().get(User.class.getName()) != null;
            Action impersonateAction = (userActionsAreRegistered) ? actionsManager.getActions().get(User.class.getName()).getAction(UserActions.IMPERSONATE) : null;
            boolean userOwnsResource = checkUserOwnsResource(user, entity);
            boolean userSharesResource = checkUserSharesResource(user, entity);
            boolean hasImpersonationPermission = impersonateAction != null && permissionImpersonation != null && hasPermission(
                    permissionImpersonation.getActionIds(), impersonateAction.getActionId());
            hasPermission = hasPermission || calculatePermission(permissionSpecific, userPermissionSpecific, hasEntityPermission, hasGeneralPermission, userOwnsResource, userSharesResource, existPermissionSpecificToEntity) || hasImpersonationPermission;
        }
        return hasPermission;
    }

    private boolean calculatePermission(Permission permissionSpecific, Permission userPermissionSpecific, boolean hasEntityPermission, boolean hasGeneralPermission, boolean userOwnsResource, boolean userSharesResource, boolean existPermissionSpecificToEntity) {
        // The value is true only if the entity permission exists and contains the
        // actionId, or if
        // the entity permission doesn't exists then the rule follow the
        // generalPermission
        // AND if the resource is an owned resource is accessed by the right user or the
        // accessing user has the impersonation permission
        return (
                ((permissionSpecific != null || userPermissionSpecific != null) && hasEntityPermission) ||
                        (permissionSpecific == null && userPermissionSpecific == null && hasGeneralPermission)) && (userOwnsResource || ((userSharesResource && !existPermissionSpecificToEntity && hasGeneralPermission) || (userSharesResource && (permissionSpecific != null || userPermissionSpecific != null) && hasEntityPermission)));
    }

    private boolean hasGeneralPermission(User user, Role r, ProtectedEntity entity, Action action) {
        Permission permission = permissionIntegrationClient.findByRoleAndResourceName(r.getId(), entity.getResourceName());
        Permission userPermission = permissionIntegrationClient.findByUserAndResourceName(user.getId(), entity.getResourceName());
        return (permission != null
                && hasPermission(permission.getActionIds(), action.getActionId())) || (userPermission != null && hasPermission(userPermission.getActionIds(), action.getActionId()));
    }

    private boolean hasEntityPermission(Permission permissionSpecific, Action action, Permission userPermissionSpecific) {
        return (permissionSpecific != null
                && hasPermission(permissionSpecific.getActionIds(), action.getActionId())) || (userPermissionSpecific != null
                && hasPermission(userPermissionSpecific.getActionIds(), action.getActionId()));
    }

    /**
     * Performs a bitwise operation between the permissionActionIds and the
     * actionId. It manipulate the bits with & operator used to compare bits of each
     * operand.
     *
     * @param permissionActionIds parameter that indicates the Permission actionIds
     * @param actionId            parameter that indicates the id of HyperIoTAction
     */
    private boolean hasPermission(long permissionActionIds, long actionId) {
        boolean hasPermission = (permissionActionIds & actionId) == actionId;
        log.debug(
                "invoking hasPermission permissionActionIds & actionId == actionId {}",
                hasPermission);
        return hasPermission;
    }

    /**
     * @param user     the current logged user
     * @param resource the current resource
     * @return true if the resource is owned by the current logged user or the
     * resource is not a owned resource, false otherwise.
     */
    public boolean checkUserOwnsResource(User user, Object resource) {
        if (user.isAdmin())
            return true;

        BaseEntity entity = (BaseEntity) resource;
        Long resourceOwnerId = null;
        //used when user shares only child entities
        boolean userSharesResource = checkUserSharesResource(user, entity);
        // looks up for a persisted entity in the hierarchy chain
        if (entity instanceof OwnedResource ownedResource) {
            resourceOwnerId = ownedResource.getOwnerUserId();
            // transient entities (id == 0) have no owner yet: handled by doCheckUserOwnsResource
            if (entity.getId() != 0 && resourceOwnerDoesNotMatch(resourceOwnerId, user, userSharesResource)) {
                return false;
            }
        }
        boolean ownsResource = doCheckUserOwnsResource(user, resourceOwnerId, resource, entity, userSharesResource);
        // A4: tenant gate on by-id access, ANDed as an ADDITIONAL necessary condition on top of the
        // existing ownership result (never weakens it). Lenient/backward-compatible: enforced only when a
        // company is active on the current SecurityContext. Admins already short-circuited above (and a
        // non-scoped admin has a null active company anyway), so there is no isAdmin() special-casing here.
        return ownsResource && checkEntityBelongsToActiveTenant(entity);
    }

    /**
     * Additional by-id tenant check: verifies the persisted entity belongs to the active company.
     * Reloads the entity from persistence (never trusts client-supplied fields) and denies cross-tenant
     * access for tenant-aware entities.
     * <p>
     * Lenient rule: returns true (no enforcement) when there is no active company on the SecurityContext,
     * for transient entities (id == 0), for non-existent entities (ownership check handles those), and for
     * entities that are not tenant-aware.
     *
     * @param entity the entity being accessed by id
     * @return true if access is allowed by the tenant rule, false to deny cross-tenant access
     */
    private boolean checkEntityBelongsToActiveTenant(BaseEntity entity) {
        Long activeCompanyId = getActiveCompanyId();
        //lenient rule: no active company => no tenant check => behaves exactly like today
        if (activeCompanyId == null)
            return true;
        //transient entity: nothing persisted to check against yet
        if (entity.getId() == 0)
            return true;
        BaseEntitySystemApi<?> service = componentRegistry.findEntitySystemApi(entity.getResourceName());
        if (service == null)
            return true;
        BaseEntity persisted = service.find(entity.getId());
        if (persisted == null)
            //non-existent entity: cannot assess tenancy; ownership/existence checks already handle it
            return true;
        if (persisted instanceof TenantResource tenantResource) {
            Long companyId = tenantResource.getCompanyId();
            //null companyId = global/cross-tenant instance, visible to every tenant
            return companyId == null || companyId.equals(activeCompanyId);
        }
        if (persisted instanceof MultiTenantResource) {
            TenantMembershipResolver resolver = findTenantMembershipResolver(persisted.getResourceName());
            if (resolver == null) {
                //no resolver registered for this M:N type: do NOT deny (mirror the lenient Api-layer seam)
                log.warn("No TenantMembershipResolver found for MultiTenantResource {}: by-id tenant check skipped", persisted.getResourceName());
                return true;
            }
            Set<Long> ids = resolver.getEntityIdsInCompany(persisted.getResourceName(), activeCompanyId);
            return ids != null && ids.contains(persisted.getId());
        }
        //not a tenant-aware entity: nothing to enforce
        return true;
    }

    /**
     * @return the active company id from the current SecurityContext (via the Runtime component), or null
     * if there is no runtime/security context/active company. Null-safe: any lookup failure is treated as
     * "no active company" (lenient), so a missing runtime never breaks a permission check.
     */
    private Long getActiveCompanyId() {
        try {
            Runtime runtime = componentRegistry.findComponent(Runtime.class, null);
            if (runtime == null)
                return null;
            SecurityContext securityContext = runtime.getSecurityContext();
            return (securityContext != null) ? securityContext.getActiveCompanyId() : null;
        } catch (Exception e) {
            log.debug("Unable to resolve Runtime/SecurityContext for tenant check, skipping: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Looks up the TenantMembershipResolver that supports the given resource type, if any.
     *
     * @param resourceName fully-qualified entity class name
     * @return the matching resolver or null if none is registered
     */
    private TenantMembershipResolver findTenantMembershipResolver(String resourceName) {
        List<TenantMembershipResolver> resolvers = componentRegistry.findComponents(TenantMembershipResolver.class, null);
        if (resolvers != null) {
            for (TenantMembershipResolver resolver : resolvers) {
                if (resolver.supports(resourceName))
                    return resolver;
            }
        }
        return null;
    }

    private boolean resourceOwnerDoesNotMatch(Long resourceOwnerId, User user, boolean userSharesResource) {
        // default-deny: a persisted OwnedResource with null/0 owner belongs to nobody (H5)
        if (userSharesResource)
            return false;
        if (resourceOwnerId == null || resourceOwnerId.longValue() == 0L)
            return true;
        return user.getId() != resourceOwnerId.longValue();
    }

    private boolean doCheckUserOwnsResource(User user, Long resourceOwnerId, Object resource, BaseEntity entity, boolean userSharesResource) {
        // create path: owner is assigned at persistence time, decision delegated to the permission check
        if (entity.getId() == 0) {
            return true;
        }
        // load the persisted entity
        BaseEntitySystemApi<?> service = componentRegistry.findEntitySystemApi(((BaseEntity) resource).getResourceName());
        if (service != null) {
            BaseEntity persistedEntity = service.find(entity.getId());
            if (persistedEntity == null) {
                // entity does not exist: it cannot be owned (or shared)
                return false;
            }
            // verify the owner
            if (persistedEntity instanceof OwnedResource ownedResource) {
                resourceOwnerId = ownedResource.getOwnerUserId();
            } else {
                // non-owned protected entity: ownership not applicable, calculatePermission
                // still ANDs this with the real permission check
                return true;
            }
        }
        // default-deny: null/0 owner means owned by nobody (H5)
        if (resourceOwnerId == null || resourceOwnerId.longValue() == 0L)
            return userSharesResource;
        return user.getId() == resourceOwnerId.longValue() || userSharesResource;
    }

    /**
     * @param user     the current logged user
     * @param resource the current resource
     * @return true if the resource is shared to the current logged user or the
     * resource is not a shared resource, false otherwise.
     */
    private boolean checkUserSharesResource(User user, Object resource) {
        if (sharedEntityIntegrationClient == null)
            return false;

        BaseEntity entity = (BaseEntity) resource;
        Collection<Long> sharedEntityIds= new ArrayList<>();
        // looks up for a persisted entity in the hierarchy chain
        boolean loop = true;
        while (loop) {
            // double check if the passed entity is consistent (must be shared to `user`)
            if (entity instanceof SharedEntity) {
                sharedEntityIds= sharedEntityIntegrationClient.fetchSharingUsersIds(entity.getResourceName(), user.getId());
                BaseEntity finalEntity = entity;
                if (sharedEntityIds.stream().noneMatch(id -> Objects.equals(id, finalEntity.getId()))){
                    return false;
                } else
                    loop = false;
            } else if (entity instanceof OwnedChildResource child) {
                if (child.getParent() != null)
                    entity = child.getParent();
                else
                    loop = false;
            } else
                loop = false;
        }

        return doCheckUserSharesResource(sharedEntityIds, user, resource, entity);
    }

    private boolean doCheckUserSharesResource(Collection<Long> sharedEntityIds, User user, Object resource, BaseEntity entity) {
        if (entity.getId() == 0)
            return false;
        // load the persisted entity
        BaseEntitySystemApi<?> service = componentRegistry.findEntitySystemApi(((BaseEntity) resource).getResourceName());
        if (service != null) {
            BaseEntity persistedEntity = service.find(entity.getId());
            // verify the owner
            if (persistedEntity instanceof SharedEntity) {
                if (sharedEntityIntegrationClient == null) {
                    sharedEntityIds = Collections.emptyList();
                } else {
                    sharedEntityIds = sharedEntityIntegrationClient.fetchSharingUsersIds(entity.getResourceName(), user.getId());
                }
            } else if (persistedEntity instanceof OwnedChildResource persistedChildEntity) {
                if (persistedChildEntity.getParent() != null) {
                    // retry with the parent resource
                    return (persistedChildEntity.getParent() == null || checkUserOwnsResource(user, persistedChildEntity.getParent()))
                            && checkUserOwnsResource(user, persistedChildEntity.getParent());
                }
                // resource is not shared so check can pass
                return true;
            }
        }
        return (sharedEntityIds.stream().anyMatch(id -> id == entity.getId()));
    }

    /**
     * @param role
     * @param resourceClass
     * @param action
     */
    @Override
    public void addPermissionIfNotExists(Role role, Class<? extends Resource> resourceClass, Action action) {
        ResourceAction<?> resourceAction = ActionFactory.createResourceAction(resourceClass, action);
        List<ResourceAction<?>> permissionList = Collections.singletonList(resourceAction);
        permissionIntegrationClient.checkOrCreatePermissions(role.getId(), permissionList);
    }
}
