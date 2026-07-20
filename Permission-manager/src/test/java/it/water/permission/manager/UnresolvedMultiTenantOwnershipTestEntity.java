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

import it.water.core.api.entity.tenant.MultiTenantResource;

import java.util.Date;

/**
 * A SECOND {@link MultiTenantResource} fixture that {@link TestOwnershipTenantMembershipResolver}
 * deliberately does NOT support, used to exercise the "no TenantMembershipResolver registered for
 * this type" branch of {@code PermissionManagerDefault#checkEntityBelongsToActiveTenant} (which
 * logs a warning and does NOT deny access, mirroring the lenient Api-layer seam).
 */
public class UnresolvedMultiTenantOwnershipTestEntity implements MultiTenantResource {
    private long id;
    private final Date entityCreateDate = new Date();
    private final Date entityModifyDate = new Date();
    private int entityVersion;

    public UnresolvedMultiTenantOwnershipTestEntity() {
    }

    public UnresolvedMultiTenantOwnershipTestEntity(long id) {
        this.id = id;
    }

    @Override
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public Date getEntityCreateDate() {
        return entityCreateDate;
    }

    @Override
    public Date getEntityModifyDate() {
        return entityModifyDate;
    }

    @Override
    public Integer getEntityVersion() {
        return entityVersion;
    }

    @Override
    public void setEntityVersion(Integer entityVersion) {
        this.entityVersion = entityVersion == null ? 0 : entityVersion;
    }
}
