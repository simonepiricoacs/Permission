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

import it.water.core.api.entity.tenant.TenantResource;

import java.util.Date;

/**
 * Minimal {@link TenantResource} fixture (single-company tenancy) used to exercise
 * {@code PermissionManagerDefault#checkUserOwnsResource(it.water.core.api.model.User, Object)}'s
 * by-id tenant gate ({@code checkEntityBelongsToActiveTenant}).
 * <p>
 * Deliberately NOT an {@code OwnedResource}/{@code SharedEntity}: for a non-owned protected
 * resource, {@code doCheckUserOwnsResource} returns {@code true} as soon as the persisted entity
 * is found, so the AND-ed tenant check is the sole decider of the overall
 * {@code checkUserOwnsResource} result in these tests.
 */
public class TenantOwnershipTestEntity implements TenantResource {
    private long id;
    private Long companyId;
    private final Date entityCreateDate = new Date();
    private final Date entityModifyDate = new Date();
    private int entityVersion;

    public TenantOwnershipTestEntity() {
    }

    public TenantOwnershipTestEntity(long id) {
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
    public Long getCompanyId() {
        return companyId;
    }

    @Override
    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
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
