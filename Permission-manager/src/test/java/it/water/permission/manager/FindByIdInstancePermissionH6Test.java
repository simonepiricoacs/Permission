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

import it.water.core.api.action.ActionsManager;
import it.water.core.api.bundle.Runtime;
import it.water.core.api.model.User;
import it.water.core.api.permission.PermissionManager;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.role.RoleManager;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.permission.action.CrudActions;
import it.water.core.permission.annotations.AllowPermissionsOnReturn;
import it.water.core.testing.utils.api.TestUserManager;
import it.water.core.testing.utils.bundle.TestRuntimeInitializer;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import it.water.permission.api.PermissionSystemApi;
import it.water.permission.model.WaterPermission;
import it.water.repository.service.BaseEntityServiceImpl;
import lombok.Setter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Security regression tests for fix H6 — find-by-id instance-level permission.
 * find(Query) must bear @AllowPermissionsOnReturn so instance-level permission is verified on
 * the returned entity: admin and owner can find(id), a non-owner with only generic FIND is
 * denied by the ownership gate (including the null/0-owner case fixed by H5).
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FindByIdInstancePermissionH6Test implements Service {

    private static final long ENTITY_ID = 42L;

    // Injected infrastructure

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Inject
    @Setter
    private Runtime runtime;

    @Inject
    @Setter
    private PermissionManager permissionManager;

    @Inject
    @Setter
    private PermissionSystemApi permissionSystemApi;

    @Inject
    @Setter
    private TestUserManager testUserManager;

    @Inject
    @Setter
    private RoleManager roleManager;

    @Inject
    @Setter
    private ActionsManager actionsManager;

    // Test fixtures

    private User adminUser;
    private User ownerUser;
    private User nonOwnerWithFindPermUser;

    /** Entity owned by ownerUser, persisted (id != 0). */
    private TestResource entityOwnedByOwner;

    /** Entity with null ownerUserId (H6 regression for null-owner fail-open). */
    private TestResource entityWithNullOwner;

    @BeforeAll
    void beforeAll() {
        adminUser = testUserManager.findUser("admin");

        ownerUser = testUserManager.addUser(
                "h6_owner", "h6_owner", "h6_owner",
                "h6_owner@mail.com", "Password1_", "salt", false);

        nonOwnerWithFindPermUser = testUserManager.addUser(
                "h6_nonowner", "h6_nonowner", "h6_nonowner",
                "h6_nonowner@mail.com", "Password1_", "salt", false);

        // Resolve the correct FIND action bitmask for TestResource.
        // DefaultActionsManager assigns 2^index per availableActions[] position.
        // TestResource @AccessControl order: SAVE=1, UPDATE=2, FIND=4, FIND_ALL=8, REMOVE=16
        long findActionId = actionsManager.getActions()
                .get(TestResource.class.getName())
                .getAction(CrudActions.FIND)
                .getActionId();

        // Give nonOwnerWithFindPermUser a role with generic (resourceId=0) FIND permission
        it.water.core.api.model.Role findRole = roleManager.createIfNotExists("h6_generic_find_role");
        roleManager.addRole(nonOwnerWithFindPermUser.getId(), findRole);
        WaterPermission genericFindPerm = new WaterPermission(
                "h6_generic_find_perm",
                findActionId,
                TestResource.class.getName(),
                0L,     // resourceId 0 = generic (all instances)
                findRole.getId(),
                0L      // userId 0 = role-based
        );
        permissionSystemApi.save(genericFindPerm);

        // Prepare test entities
        entityOwnedByOwner = new TestResource();
        entityOwnedByOwner.setId(ENTITY_ID);
        entityOwnedByOwner.setOwnerUserId(ownerUser.getId());

        entityWithNullOwner = new TestResource();
        entityWithNullOwner.setId(ENTITY_ID + 1);
        entityWithNullOwner.setOwnerUserId(null);

        // Seed the systemApi stub with the owned entity
        TestResourceSystemApi systemApi =
                componentRegistry.findComponent(TestResourceSystemApi.class, null);
        systemApi.returnEntity(entityOwnedByOwner);

        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    // H6-1: @AllowPermissionsOnReturn annotation is present on find(Query filter)
    //        This is the structural contract that enables per-instance authorization.

    @Test
    @Order(20)
    void testH6FindQueryMethodHasAllowPermissionsOnReturnAnnotation() throws NoSuchMethodException {
        Method findQueryMethod = BaseEntityServiceImpl.class.getDeclaredMethod(
                "find", it.water.core.api.repository.query.Query.class);
        Assertions.assertTrue(
                findQueryMethod.isAnnotationPresent(AllowPermissionsOnReturn.class),
                "find(Query) in BaseEntityServiceImpl must carry @AllowPermissionsOnReturn " +
                "to enforce per-instance authorization on the returned entity (H6 contract)"
        );
        String[] actions = findQueryMethod.getAnnotation(AllowPermissionsOnReturn.class).actions();
        Assertions.assertTrue(
                Arrays.asList(actions).contains(CrudActions.FIND),
                "@AllowPermissionsOnReturn on find(Query) must list the FIND action"
        );
    }

    // H6-2: admin bypasses ownership — always allowed to find(id)

    @Test
    @Order(21)
    void testH6AdminCanFindEntityWithNullOwner() {
        TestRuntimeInitializer.getInstance().impersonate(adminUser, runtime);
        // Admin should pass checkPermission regardless of ownerUserId
        boolean result = permissionManager.checkPermission(
                adminUser.getUsername(), entityWithNullOwner,
                actionsManager.getActions().get(TestResource.class.getName()).getAction(CrudActions.FIND));
        Assertions.assertTrue(result,
                "Admin must pass checkPermission for a null-owner entity (H6 assertion)");
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    // H6-3: entity owner with FIND permission can access their entity

    @Test
    @Order(22)
    void testH6OwnerCanAccessOwnEntity() {
        // Grant ownerUser the FIND permission as well
        long findActionId = actionsManager.getActions()
                .get(TestResource.class.getName())
                .getAction(CrudActions.FIND)
                .getActionId();
        it.water.core.api.model.Role ownerFindRole = roleManager.createIfNotExists("h6_owner_find_role");
        roleManager.addRole(ownerUser.getId(), ownerFindRole);
        WaterPermission ownerFindPerm = new WaterPermission(
                "h6_owner_find_perm",
                findActionId,
                TestResource.class.getName(),
                0L,
                ownerFindRole.getId(),
                0L
        );
        permissionSystemApi.save(ownerFindPerm);
        TestRuntimeUtils.impersonateAdmin(componentRegistry);

        TestRuntimeInitializer.getInstance().impersonate(ownerUser, runtime);
        boolean result = permissionManager.checkPermission(
                ownerUser.getUsername(), entityOwnedByOwner,
                actionsManager.getActions().get(TestResource.class.getName()).getAction(CrudActions.FIND));
        Assertions.assertTrue(result,
                "Owner with FIND permission must pass checkPermission for their own entity");
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    // H6-4: non-owner with generic FIND permission is denied when ownerUserId is null
    //        This tests the H6 scenario: prior to H5 fix, null owner returned true
    //        (fail-open IDOR). After H5+H6 fix, null owner on a persisted entity returns false.

    @Test
    @Order(23)
    void testH6NonOwnerGenericFindPermissionNullOwnerEntityIsDenied() {
        TestRuntimeInitializer.getInstance().impersonate(nonOwnerWithFindPermUser, runtime);
        // entityWithNullOwner has null ownerUserId and id != 0 (persisted entity).
        // After H5 fix: null owner = deny non-admin.
        // After H6 contract: @AllowPermissionsOnReturn must enforce this denial on find(Query).
        boolean result = permissionManager.checkPermission(
                nonOwnerWithFindPermUser.getUsername(), entityWithNullOwner,
                actionsManager.getActions().get(TestResource.class.getName()).getAction(CrudActions.FIND));
        Assertions.assertFalse(result,
                "Non-admin user with only generic FIND must be denied access to a persisted entity " +
                "whose ownerUserId is null (H6 regression: null owner must not be fail-open)");
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    // H6-5: non-owner with generic FIND permission is denied when owner is another user

    @Test
    @Order(24)
    void testH6NonOwnerGenericFindPermissionWrongOwnerEntityIsDenied() {
        TestRuntimeInitializer.getInstance().impersonate(nonOwnerWithFindPermUser, runtime);
        // entityOwnedByOwner has ownerUserId = ownerUser.getId()
        // nonOwnerWithFindPermUser != ownerUser → should be denied
        boolean result = permissionManager.checkPermission(
                nonOwnerWithFindPermUser.getUsername(), entityOwnedByOwner,
                actionsManager.getActions().get(TestResource.class.getName()).getAction(CrudActions.FIND));
        Assertions.assertFalse(result,
                "Non-admin user with generic FIND must be denied access to an entity owned by another user (H6)");
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }
}
