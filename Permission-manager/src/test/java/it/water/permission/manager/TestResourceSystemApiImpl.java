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

/**
 * Minimal in-memory stub for TestResource system API.
 * Stores a single TestResource instance that can be pre-configured via returnEntity().
 * Used by PermissionManagerDefaultTest and H6 security regression tests.
 */
@FrameworkComponent(services = TestResourceSystemApi.class)
public class TestResourceSystemApiImpl implements TestResourceSystemApi {

    private TestResource returnedEntity;

    @Override
    public void returnEntity(TestResource resource) {
        this.returnedEntity = resource;
    }

    @Override
    public TestResource save(TestResource entity) {
        return update(entity);
    }

    @Override
    public TestResource update(TestResource entity) {
        this.returnedEntity = entity;
        return entity;
    }

    @Override
    public void remove(long id) {
        if (returnedEntity != null && returnedEntity.getId() == id) {
            returnedEntity = null;
        }
    }

    @Override
    public TestResource find(long id) {
        if (returnedEntity != null && returnedEntity.getId() == id) {
            return returnedEntity;
        }
        return null;
    }

    @Override
    public TestResource find(Query filter) {
        // Simplified: return the stored entity regardless of filter (sufficient for tests)
        return returnedEntity;
    }

    @Override
    public PaginableResult<TestResource> findAll(Query filter, int delta, int page, QueryOrder queryOrder) {
        java.util.List<TestResource> results = new java.util.ArrayList<>();
        if (returnedEntity != null) results.add(returnedEntity);
        // PaginatedResult(numPages, currentPage, nextPage, delta, results)
        return new it.water.repository.entity.model.PaginatedResult<>(1, page, page, delta, results);
    }

    @Override
    public long countAll(Query filter) {
        return returnedEntity != null ? 1L : 0L;
    }

    @Override
    public Class<TestResource> getEntityType() {
        return TestResource.class;
    }

    @Override
    public QueryBuilder getQueryBuilderInstance() {
        return new QueryBuilder() {
            @Override
            public Query createQueryFilter(String filter) {
                // Return a simple stub query
                return new StubQuery(filter);
            }

            @Override
            public FieldNameOperand field(String name) {
                return new FieldNameOperand(name);
            }
        };
    }

    // ------------------------------------------------------------------
    // Minimal stub Query used by getQueryBuilderInstance
    // ------------------------------------------------------------------

    static final class StubQuery implements Query {
        private final String definition;

        StubQuery(String definition) {
            this.definition = definition;
        }

        @Override
        public void defineOperands(Query... operands) { /* stub */ }

        @Override
        public String getDefinition() {
            return definition;
        }

        @Override
        public Query and(Query rightQuery) {
            return new StubQuery("(" + definition + " AND " + rightQuery.getDefinition() + ")");
        }

        @Override
        public Query or(Query rightQuery) {
            return new StubQuery("(" + definition + " OR " + rightQuery.getDefinition() + ")");
        }

        @Override
        public Query in(java.util.List<?> values) {
            return new StubQuery(definition + " IN " + values);
        }

        @Override
        public Query not() {
            return new StubQuery("NOT(" + definition + ")");
        }
    }
}
