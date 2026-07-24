package it.water.permission.manager;

import it.water.core.api.bundle.Runtime;
import it.water.core.api.entity.tenant.TenantResource;
import it.water.core.api.model.Role;
import it.water.core.api.model.User;
import it.water.core.api.permission.SecurityContext;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.service.integration.RoleIntegrationClient;
import it.water.core.api.service.integration.UserIntegrationClient;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Company-aware role filtering (DC-T1): a user assigned a role in company 1 and a different role in
 * company 2 must only "have" the role of the active company. Verifies {@link PermissionManagerDefault}
 * directly, because the Water test runtime uses a different, in-memory permission manager that this
 * fix intentionally does not touch.
 */
class PermissionManagerCompanyRoleFilterTest {

    private static final String USERNAME = "mario";
    private static final long USER_ID = 123L;

    /** Builds a PermissionManagerDefault wired to mocks, with the given active company and user roles. */
    private PermissionManagerDefault manager(Long activeCompanyId, Collection<Role> userRoles) {
        UserIntegrationClient userClient = mock(UserIntegrationClient.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(userClient.fetchUserByUsername(USERNAME)).thenReturn(user);

        RoleIntegrationClient roleClient = mock(RoleIntegrationClient.class);
        when(roleClient.fetchUserRoles(USER_ID)).thenReturn(userRoles);

        Runtime runtime = mock(Runtime.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getActiveCompanyId()).thenReturn(activeCompanyId);
        when(runtime.getSecurityContext()).thenReturn(securityContext);
        ComponentRegistry registry = mock(ComponentRegistry.class);
        when(registry.findComponent(eq(Runtime.class), isNull())).thenReturn(runtime);

        PermissionManagerDefault permissionManager = new PermissionManagerDefault();
        permissionManager.setUserIntegrationClient(userClient);
        permissionManager.setRoleIntegrationClient(roleClient);
        permissionManager.setComponentRegistry(registry);
        return permissionManager;
    }

    /** A role scoped to a company (companyId == null means a global role). */
    private Role companyRole(String name, Long companyId) {
        Role role = mock(Role.class, withSettings().extraInterfaces(TenantResource.class));
        when(role.getName()).thenReturn(name);
        when(((TenantResource) role).getCompanyId()).thenReturn(companyId);
        return role;
    }

    @Test
    void userHasOnlyTheRoleOfTheActiveCompany() {
        Collection<Role> roles = Arrays.asList(
                companyRole("Manager", 1L),
                companyRole("Viewer", 2L));

        PermissionManagerDefault company1 = manager(1L, roles);
        assertTrue(company1.userHasRoles(USERNAME, new String[]{"Manager"}),
                "con company 1 attiva l'utente deve avere Manager");
        assertFalse(company1.userHasRoles(USERNAME, new String[]{"Viewer"}),
                "con company 1 attiva l'utente NON deve avere Viewer (e' della company 2)");

        PermissionManagerDefault company2 = manager(2L, roles);
        assertTrue(company2.userHasRoles(USERNAME, new String[]{"Viewer"}),
                "con company 2 attiva l'utente deve avere Viewer");
        assertFalse(company2.userHasRoles(USERNAME, new String[]{"Manager"}),
                "con company 2 attiva l'utente NON deve avere Manager (e' della company 1)");
    }

    @Test
    void withNoActiveCompanyAllRolesRemainVisible() {
        Collection<Role> roles = Arrays.asList(
                companyRole("Manager", 1L),
                companyRole("Viewer", 2L));
        PermissionManagerDefault lenient = manager(null, roles);
        assertTrue(lenient.userHasRoles(USERNAME, new String[]{"Manager"}));
        assertTrue(lenient.userHasRoles(USERNAME, new String[]{"Viewer"}),
                "senza company attiva (regola lenient) tutti i ruoli restano visibili");
    }

    @Test
    void globalRoleIsVisibleInAnyCompany() {
        Collection<Role> roles = Collections.singletonList(companyRole("Support", null));
        PermissionManagerDefault company1 = manager(1L, roles);
        assertTrue(company1.userHasRoles(USERNAME, new String[]{"Support"}),
                "un ruolo globale (companyId null) e' visibile in qualsiasi company");
    }
}
