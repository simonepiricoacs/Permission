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

import it.water.core.api.bundle.Runtime;
import it.water.core.api.model.User;
import it.water.core.api.permission.PermissionManager;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.testing.utils.api.TestUserManager;
import it.water.core.testing.utils.bundle.TestRuntimeInitializer;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import lombok.Setter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Security regression tests for fix H5 — ownership default-deny in
 * PermissionManagerDefault.checkUserOwnsResource: null/0 owner is denied for non-admins,
 * sharing and admin override the deny, transient entities (id == 0) pass (create path).
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionManagerOwnershipSecurityH5Test implements Service {

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
    private TestUserManager testUserManager;

    private User adminUser;
    private User ownerUser;
    private User otherUser;

    private OwnedOnlyResource ownedByOwner;
    private OwnedOnlyResource ownedByNobodyNull;
    private OwnedOnlyResource ownedByNobodyZero;
    private OwnedOnlyResource transientOwned;

    @BeforeAll
    void beforeAll() {
        adminUser = testUserManager.findUser("admin");
        ownerUser = testUserManager.addUser(
                "h5_owner", "h5_owner", "h5_owner",
                "h5_owner@mail.com", "Password1_", "salt", false);
        otherUser = testUserManager.addUser(
                "h5_other", "h5_other", "h5_other",
                "h5_other@mail.com", "Password1_", "salt", false);

        ownedByOwner = new OwnedOnlyResource(1001L, ownerUser.getId());
        ownedByNobodyNull = new OwnedOnlyResource(1002L, null);
        ownedByNobodyZero = new OwnedOnlyResource(1003L, 0L);
        transientOwned = new OwnedOnlyResource(0L, null);
    }

    @Test
    @Order(10)
    void testH5NullOwnerNonAdminIsDenied() {
        TestRuntimeInitializer.getInstance().impersonate(ownerUser, runtime);
        try {
            boolean result = permissionManager.checkUserOwnsResource(ownerUser, ownedByNobodyNull);
            Assertions.assertFalse(result,
                    "A non-admin user must NOT own a persisted entity whose ownerUserId is null");
        } finally {
            TestRuntimeUtils.impersonateAdmin(componentRegistry);
        }
    }

    @Test
    @Order(11)
    void testH5ZeroOwnerNonAdminIsDenied() {
        TestRuntimeInitializer.getInstance().impersonate(ownerUser, runtime);
        try {
            boolean result = permissionManager.checkUserOwnsResource(ownerUser, ownedByNobodyZero);
            Assertions.assertFalse(result,
                    "A non-admin user must NOT own a persisted entity whose ownerUserId is 0");
        } finally {
            TestRuntimeUtils.impersonateAdmin(componentRegistry);
        }
    }

    @Test
    @Order(12)
    void testH5WrongOwnerIsDenied() {
        TestRuntimeInitializer.getInstance().impersonate(otherUser, runtime);
        try {
            boolean result = permissionManager.checkUserOwnsResource(otherUser, ownedByOwner);
            Assertions.assertFalse(result,
                    "A user must NOT own a persisted entity owned by a different user");
        } finally {
            TestRuntimeUtils.impersonateAdmin(componentRegistry);
        }
    }

    @Test
    @Order(13)
    void testH5CorrectOwnerIsAllowed() {
        TestRuntimeInitializer.getInstance().impersonate(ownerUser, runtime);
        try {
            boolean result = permissionManager.checkUserOwnsResource(ownerUser, ownedByOwner);
            Assertions.assertTrue(result,
                    "The owning user must be allowed to own their own persisted entity");
        } finally {
            TestRuntimeUtils.impersonateAdmin(componentRegistry);
        }
    }

    @Test
    @Order(14)
    void testH5AdminBypassesNullOwner() {
        TestRuntimeInitializer.getInstance().impersonate(adminUser, runtime);
        boolean result = permissionManager.checkUserOwnsResource(adminUser, ownedByNobodyNull);
        Assertions.assertTrue(result,
                "Admin must always be allowed regardless of ownerUserId (null case)");
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    @Test
    @Order(15)
    void testH5AdminBypassesZeroOwner() {
        TestRuntimeInitializer.getInstance().impersonate(adminUser, runtime);
        boolean result = permissionManager.checkUserOwnsResource(adminUser, ownedByNobodyZero);
        Assertions.assertTrue(result,
                "Admin must always be allowed regardless of ownerUserId (zero case)");
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    @Test
    @Order(16)
    void testH5TransientEntityCreatePathIsAllowed() {
        TestRuntimeInitializer.getInstance().impersonate(ownerUser, runtime);
        try {
            boolean result = permissionManager.checkUserOwnsResource(ownerUser, transientOwned);
            Assertions.assertTrue(result,
                    "A transient entity (id == 0) must not block the create path for non-admin");
        } finally {
            TestRuntimeUtils.impersonateAdmin(componentRegistry);
        }
    }

    @Test
    @Order(17)
    void testH5NullOwnerSharedWithUserIsAllowed() {
        //unwrap the FakeSharingIntegrationClient from its test proxy
        it.water.core.api.service.integration.SharedEntityIntegrationClient sharedClient =
                TestRuntimeInitializer.getInstance()
                        .getComponentRegistry()
                        .findComponent(it.water.core.api.service.integration.SharedEntityIntegrationClient.class, null);
        java.lang.reflect.InvocationHandler handler = java.lang.reflect.Proxy.getInvocationHandler(sharedClient);
        it.water.core.testing.utils.interceptors.TestServiceProxy<?> proxy =
                (it.water.core.testing.utils.interceptors.TestServiceProxy<?>) handler;
        FakeSharingIntegrationClient fakeClient =
                (FakeSharingIntegrationClient) proxy.getRealService();

        fakeClient.clearAll();
        fakeClient.addId(ownedByNobodyNull.getId());

        TestRuntimeInitializer.getInstance().impersonate(ownerUser, runtime);
        try {
            //FakeSharingIntegrationClient.fetchSharingUsersIds always returns List.of(1L),
            //so the shared entity id must be 1 to match
            SharedOwnedResource sharedResourceMatchingFake = new SharedOwnedResource(1L, null);
            boolean result = permissionManager.checkUserOwnsResource(ownerUser, sharedResourceMatchingFake);
            Assertions.assertTrue(result,
                    "A shared resource should be accessible even when ownerUserId is null");
        } finally {
            fakeClient.clearAll();
            TestRuntimeUtils.impersonateAdmin(componentRegistry);
        }
    }

    /**
     * Minimal OwnedResource without a registered SystemApi: doCheckUserOwnsResource falls
     * through to the direct ownerUserId comparison.
     */
    static final class OwnedOnlyResource implements it.water.core.api.entity.owned.OwnedResource {

        private final long id;
        private Long ownerUserId;

        OwnedOnlyResource(long id, Long ownerUserId) {
            this.id = id;
            this.ownerUserId = ownerUserId;
        }

        @Override
        public long getId() {
            return id;
        }

        @Override
        public Long getOwnerUserId() {
            return ownerUserId;
        }

        @Override
        public void setOwnerUserId(Long userId) {
            this.ownerUserId = userId;
        }

        @Override
        public java.util.Date getEntityCreateDate() {
            return null;
        }

        @Override
        public java.util.Date getEntityModifyDate() {
            return null;
        }

        @Override
        public Integer getEntityVersion() {
            return 1;
        }

        @Override
        public void setEntityVersion(Integer version) {
            // not required for testing
        }
    }

    /** OwnedResource + SharedEntity, so checkUserSharesResource can fire. */
    static final class SharedOwnedResource implements it.water.core.api.entity.shared.SharedEntity {

        private final long id;
        private Long ownerUserId;

        SharedOwnedResource(long id, Long ownerUserId) {
            this.id = id;
            this.ownerUserId = ownerUserId;
        }

        @Override
        public long getId() {
            return id;
        }

        @Override
        public Long getOwnerUserId() {
            return ownerUserId;
        }

        @Override
        public void setOwnerUserId(Long userId) {
            this.ownerUserId = userId;
        }

        @Override
        public java.util.Date getEntityCreateDate() {
            return null;
        }

        @Override
        public java.util.Date getEntityModifyDate() {
            return null;
        }

        @Override
        public Integer getEntityVersion() {
            return 1;
        }

        @Override
        public void setEntityVersion(Integer version) {
            // not required for testing
        }
    }
}
