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
package org.opennms.netmgt.telemetry.protocols.sflow.parser.proto.flows;

import org.bson.BsonWriter;
import org.opennms.netmgt.telemetry.listeners.utils.BufferUtils;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.SampleDatagramEnrichment;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.InvalidPacketException;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.SampleDatagramVisitor;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.proto.AsciiString;

import com.google.common.base.MoreObjects;

import io.netty.buffer.ByteBuf;

// struct extended_mpls_FTN {
//    string mplsFTNDescr<>;
//    unsigned int mplsFTNMask;
// };

public class ExtendedMplsFtn implements FlowData {
    public final AsciiString mplsFTNDescr;
    public final long mplsFTNMask;

    public ExtendedMplsFtn(final ByteBuf buffer) throws InvalidPacketException {
        this.mplsFTNDescr = new AsciiString(buffer);
        this.mplsFTNMask = BufferUtils.uint32(buffer);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("mplsFTNDescr", this.mplsFTNDescr)
                .add("mplsFTNMask", this.mplsFTNMask)
                .toString();
    }

    @Override
    public void writeBson(final BsonWriter bsonWriter, final SampleDatagramEnrichment enr) {
        bsonWriter.writeStartDocument();
        bsonWriter.writeString("mplsFTNDescr", this.mplsFTNDescr.value);
        bsonWriter.writeInt64("mplsFTNMask", this.mplsFTNMask);
        bsonWriter.writeEndDocument();
    }

    @Override
    public void visit(final SampleDatagramVisitor visitor) {
        visitor.accept(this);
    }
}
