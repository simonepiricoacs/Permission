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

import it.water.core.api.model.PaginableResult;
import it.water.core.api.repository.query.Query;
import it.water.core.api.repository.query.QueryBuilder;
import it.water.core.api.repository.query.QueryOrder;
import it.water.core.api.repository.query.operands.FieldNameOperand;
import it.water.core.interceptors.annotations.FrameworkComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal in-memory stub for {@link MultiTenantOwnershipTestEntity}'s system API, auto-discovered
 * by {@code WaterTestExtension}'s classpath scan. Backs
 * {@code PermissionManagerDefault#checkEntityBelongsToActiveTenant}'s reload-by-id via
 * {@code ComponentRegistry.findEntitySystemApi}.
 */
@FrameworkComponent(services = MultiTenantOwnershipTestEntitySystemApi.class)
public class MultiTenantOwnershipTestEntitySystemApiImpl implements MultiTenantOwnershipTestEntitySystemApi {

    private final Map<Long, MultiTenantOwnershipTestEntity> store = new HashMap<>();

    @Override
    public void seed(MultiTenantOwnershipTestEntity entity) {
        store.put(entity.getId(), entity);
    }

    @Override
    public MultiTenantOwnershipTestEntity save(MultiTenantOwnershipTestEntity entity) {
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public MultiTenantOwnershipTestEntity update(MultiTenantOwnershipTestEntity entity) {
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public void remove(long id) {
        store.remove(id);
    }

    @Override
    public MultiTenantOwnershipTestEntity find(long id) {
        return store.get(id);
    }

    @Override
    public MultiTenantOwnershipTestEntity find(Query filter) {
        return store.values().stream().findFirst().orElse(null);
    }

    @Override
    public PaginableResult<MultiTenantOwnershipTestEntity> findAll(Query filter, int delta, int page, QueryOrder queryOrder) {
        List<MultiTenantOwnershipTestEntity> results = new ArrayList<>(store.values());
        return new it.water.repository.entity.model.PaginatedResult<>(1, page, page, delta, results);
    }

    @Override
    public long countAll(Query filter) {
        return store.size();
    }

    @Override
    public Class<MultiTenantOwnershipTestEntity> getEntityType() {
        return MultiTenantOwnershipTestEntity.class;
    }

    @Override
    public QueryBuilder getQueryBuilderInstance() {
        return new QueryBuilder() {
            @Override
            public Query createQueryFilter(String filter) {
                return null;
            }

            @Override
            public FieldNameOperand field(String name) {
                return new FieldNameOperand(name);
            }
        };
    }
}
