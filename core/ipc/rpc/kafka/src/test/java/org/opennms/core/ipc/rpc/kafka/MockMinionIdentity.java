/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.core.ipc.rpc.kafka;


import org.opennms.distributed.core.api.MinionIdentity;
import org.opennms.distributed.core.api.SystemType;

public class MockMinionIdentity implements MinionIdentity {

    private final String location;
    
    public MockMinionIdentity(String location) {
        this.location = location;
    }

    @Override
    public String getId() {
        return "minionId";
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public String getType() {
        return SystemType.Minion.name();
    }
}
