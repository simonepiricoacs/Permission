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

import it.water.core.api.service.BaseEntitySystemApi;

public interface TenantOwnershipTestEntitySystemApi extends BaseEntitySystemApi<TenantOwnershipTestEntity> {
    /**
     * Seeds (or replaces) a persisted row, bypassing save()'s auto-assign semantics: tests fully
     * control the persisted companyId this way, mirroring the SystemApi-seeding technique used in
     * Repository-service's tenant coverage tests.
     */
    void seed(TenantOwnershipTestEntity entity);
}
