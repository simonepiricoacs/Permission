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
import it.water.core.testing.utils.security.TestSecurityContext;
import lombok.Setter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;

/**
 * Coverage tests for the multitenancy by-id gate added to
 * {@code PermissionManagerDefault#checkUserOwnsResource(User, Object)}: the ANDed
 * {@code checkEntityBelongsToActiveTenant} private helper, its {@code getActiveCompanyId()} and
 * {@code findTenantMembershipResolver(String)} companions, exercised from Permission-manager's OWN
 * test scope (no JPA/H2 involved: {@link TenantOwnershipTestEntitySystemApiImpl},
 * {@link MultiTenantOwnershipTestEntitySystemApiImpl} and
 * {@link UnresolvedMultiTenantOwnershipTestEntitySystemApiImpl} are private in-memory stores
 * auto-registered by this module's {@code WaterTestExtension} classpath scan).
 * <p>
 * All scenarios use a NON-admin user: {@code checkUserOwnsResource} short-circuits
 * {@code true} for admins BEFORE the tenant gate is ever reached, so the tenant gate can only be
 * observed with a non-admin caller. Since the fixture entities are neither {@code OwnedResource}
 * nor {@code SharedEntity}, {@code doCheckUserOwnsResource} always resolves to {@code true} once
 * the persisted row is found, making the ANDed tenant check the sole decider of the assertions
 * below.
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionManagerTenantMultitenancyCoverageTest implements Service {

    private static final Long COMPANY_A = 8801L;
    private static final Long COMPANY_B = 8802L;

    private static final long TENANT_ROW_OWN_COMPANY_ID = 501L;
    private static final long TENANT_ROW_OTHER_COMPANY_ID = 502L;
    private static final long TENANT_ROW_GLOBAL_ID = 503L;

    private static final long MT_ROW_MEMBER_ID = 601L;
    private static final long MT_ROW_NOT_MEMBER_ID = 602L;

    private static final long UNRESOLVED_ROW_ID = 701L;

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Inject
    @Setter
    private Runtime runtime;

    @Inject
    @Setter
    private TestUserManager testUserManager;

    @Inject
    @Setter
    private PermissionManager permissionManager;

    private User adminUser;
    private User nonAdminUser;

    private TenantOwnershipTestEntitySystemApi tenantSystemApi;
    private MultiTenantOwnershipTestEntitySystemApi multiTenantSystemApi;
    private UnresolvedMultiTenantOwnershipTestEntitySystemApi unresolvedSystemApi;

    @BeforeAll
    void initializeFixtures() {
        adminUser = testUserManager.findUser("admin");
        nonAdminUser = testUserManager.addUser("tenantGateNonAdmin", "Tenant", "Gate",
                "tenant.gate.nonadmin@mail.com", "Password1_", "salt", false);

        tenantSystemApi = componentRegistry.findComponent(TenantOwnershipTestEntitySystemApi.class, null);
        multiTenantSystemApi = componentRegistry.findComponent(MultiTenantOwnershipTestEntitySystemApi.class, null);
        unresolvedSystemApi = componentRegistry.findComponent(UnresolvedMultiTenantOwnershipTestEntitySystemApi.class, null);

        TenantOwnershipTestEntity ownCompanyRow = new TenantOwnershipTestEntity(TENANT_ROW_OWN_COMPANY_ID);
        ownCompanyRow.setCompanyId(COMPANY_A);
        TenantOwnershipTestEntity otherCompanyRow = new TenantOwnershipTestEntity(TENANT_ROW_OTHER_COMPANY_ID);
        otherCompanyRow.setCompanyId(COMPANY_B);
        TenantOwnershipTestEntity globalRow = new TenantOwnershipTestEntity(TENANT_ROW_GLOBAL_ID);
        globalRow.setCompanyId(null);
        tenantSystemApi.seed(ownCompanyRow);
        tenantSystemApi.seed(otherCompanyRow);
        tenantSystemApi.seed(globalRow);

        MultiTenantOwnershipTestEntity memberRow = new MultiTenantOwnershipTestEntity(MT_ROW_MEMBER_ID);
        MultiTenantOwnershipTestEntity notMemberRow = new MultiTenantOwnershipTestEntity(MT_ROW_NOT_MEMBER_ID);
        multiTenantSystemApi.seed(memberRow);
        multiTenantSystemApi.seed(notMemberRow);
        TestOwnershipTenantMembershipResolver.setMembership(COMPANY_A, Set.of(MT_ROW_MEMBER_ID));

        UnresolvedMultiTenantOwnershipTestEntity unresolvedRow = new UnresolvedMultiTenantOwnershipTestEntity(UNRESOLVED_ROW_ID);
        unresolvedSystemApi.seed(unresolvedRow);
    }

    @AfterAll
    void restoreCleanContext() {
        //restores a non-scoped admin context so no later test class in the shared JVM/registry
        //inherits an active company from this one.
        TestRuntimeInitializer.getInstance().impersonate(adminUser, runtime);
    }

    private void activateCompany(Long companyId) {
        runtime.fillSecurityContext(TestSecurityContext.createContext(nonAdminUser.getId(), nonAdminUser.getUsername(), false, companyId));
    }

    private void clearActiveCompany() {
        //3-arg overload: non-admin, non-scoped (activeCompanyId = null)
        TestRuntimeInitializer.getInstance().impersonate(nonAdminUser, runtime);
    }

    // -----------------------------------------------------------------------
    // TenantResource: single-company by-id gate
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    void checkUserOwnsResource_tenantResource_ownCompany_allowed() {
        activateCompany(COMPANY_A);

        TenantOwnershipTestEntity handle = new TenantOwnershipTestEntity(TENANT_ROW_OWN_COMPANY_ID);

        Assertions.assertTrue(permissionManager.checkUserOwnsResource(nonAdminUser, handle),
                "own-company row must be allowed for a non-admin caller scoped to the same company");
    }

    @Test
    @Order(2)
    void checkUserOwnsResource_tenantResource_globalNullCompany_allowed() {
        activateCompany(COMPANY_A);

        TenantOwnershipTestEntity handle = new TenantOwnershipTestEntity(TENANT_ROW_GLOBAL_ID);

        Assertions.assertTrue(permissionManager.checkUserOwnsResource(nonAdminUser, handle),
                "a null-companyId (global) persisted row must be visible cross-tenant");
    }

    @Test
    @Order(3)
    void checkUserOwnsResource_tenantResource_otherCompany_denied() {
        activateCompany(COMPANY_A);

        TenantOwnershipTestEntity handle = new TenantOwnershipTestEntity(TENANT_ROW_OTHER_COMPANY_ID);

        Assertions.assertFalse(permissionManager.checkUserOwnsResource(nonAdminUser, handle),
                "a row persisted under a different company must be denied for a non-admin caller");
    }

    @Test
    @Order(4)
    void checkUserOwnsResource_tenantResource_noActiveCompany_backwardCompatibleAllowed() {
        clearActiveCompany();

        TenantOwnershipTestEntity handle = new TenantOwnershipTestEntity(TENANT_ROW_OTHER_COMPANY_ID);

        Assertions.assertTrue(permissionManager.checkUserOwnsResource(nonAdminUser, handle),
                "with no active company the by-id tenant gate must not deny access (lenient/backward-compatible)");
    }

    // -----------------------------------------------------------------------
    // MultiTenantResource: resolver-backed M:N by-id gate
    // -----------------------------------------------------------------------

    @Test
    @Order(5)
    void checkUserOwnsResource_multiTenantResource_resolverMember_allowed() {
        activateCompany(COMPANY_A);

        MultiTenantOwnershipTestEntity handle = new MultiTenantOwnershipTestEntity(MT_ROW_MEMBER_ID);

        Assertions.assertTrue(permissionManager.checkUserOwnsResource(nonAdminUser, handle),
                "a row whose id is in the resolver's membership set for the active company must be allowed");
    }

    @Test
    @Order(6)
    void checkUserOwnsResource_multiTenantResource_resolverNotMember_denied() {
        activateCompany(COMPANY_A);

        MultiTenantOwnershipTestEntity handle = new MultiTenantOwnershipTestEntity(MT_ROW_NOT_MEMBER_ID);

        Assertions.assertFalse(permissionManager.checkUserOwnsResource(nonAdminUser, handle),
                "a row whose id is NOT in the resolver's membership set for the active company must be denied");
    }

    @Test
    @Order(7)
    void checkUserOwnsResource_multiTenantResource_noResolverForType_notDenied() {
        activateCompany(COMPANY_A);

        UnresolvedMultiTenantOwnershipTestEntity handle = new UnresolvedMultiTenantOwnershipTestEntity(UNRESOLVED_ROW_ID);

        Assertions.assertTrue(permissionManager.checkUserOwnsResource(nonAdminUser, handle),
                "with no TenantMembershipResolver registered for this MultiTenantResource type, access must NOT be denied (lenient, warn-and-skip)");
    }

    @Test
    @Order(8)
    void checkUserOwnsResource_multiTenantResource_noActiveCompany_backwardCompatibleAllowed() {
        clearActiveCompany();

        MultiTenantOwnershipTestEntity handle = new MultiTenantOwnershipTestEntity(MT_ROW_NOT_MEMBER_ID);

        Assertions.assertTrue(permissionManager.checkUserOwnsResource(nonAdminUser, handle),
                "with no active company the by-id tenant gate must not deny access, even for a row absent from every membership set");
    }
}
