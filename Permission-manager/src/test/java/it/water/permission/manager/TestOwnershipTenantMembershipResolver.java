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

import it.water.core.api.service.integration.TenantMembershipResolver;
import it.water.core.interceptors.annotations.FrameworkComponent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only {@link TenantMembershipResolver}, auto-discovered by {@code WaterTestExtension}'s
 * classpath scan and shared as a singleton across this module's whole test run.
 * <p>
 * It supports ONLY {@link MultiTenantOwnershipTestEntity} (by design), so a SIBLING
 * {@code MultiTenantResource} entity type ({@link UnresolvedMultiTenantOwnershipTestEntity}) can
 * be used to exercise the "no resolver registered for this type" branch of
 * {@code PermissionManagerDefault#checkEntityBelongsToActiveTenant}.
 * <p>
 * Company membership is fully controllable from the test via {@link #setMembership(long, Set)}.
 */
@FrameworkComponent(services = TenantMembershipResolver.class)
public class TestOwnershipTenantMembershipResolver implements TenantMembershipResolver {

    private static final Map<Long, Set<Long>> MEMBERSHIP = new ConcurrentHashMap<>();

    public static void setMembership(long companyId, Set<Long> memberIds) {
        MEMBERSHIP.put(companyId, new HashSet<>(memberIds));
    }

    @Override
    public boolean supports(String entityResourceName) {
        return MultiTenantOwnershipTestEntity.class.getName().equals(entityResourceName);
    }

    @Override
    public Set<Long> getEntityIdsInCompany(String entityResourceName, long companyId) {
        if (!supports(entityResourceName))
            return Collections.emptySet();
        return MEMBERSHIP.getOrDefault(companyId, Collections.emptySet());
    }
}
