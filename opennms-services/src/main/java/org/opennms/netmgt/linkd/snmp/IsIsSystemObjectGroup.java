/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.linkd.snmp;

import java.net.InetAddress;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opennms.netmgt.snmp.AggregateTracker;
import org.opennms.netmgt.snmp.SnmpResult;

public final class IsIsSystemObjectGroup extends AggregateTracker {
    
    
    private static final Logger LOG = LoggerFactory.getLogger(IsIsSystemObjectGroup.class);

    public enum IsisAdminState {
        ON(1),
        OFF(2);
        
        private final Integer m_value;
        private static final HashMap<Integer, IsisAdminState> m_valueMap = new HashMap<Integer, IsisAdminState>();
        
        static {
            for (IsisAdminState state: IsisAdminState.values()) {
                m_valueMap.put(state.getValue(), state);
            }
        }
        private IsisAdminState(Integer value) {
            m_value = value;
        }
        
        public Integer getValue() {
            return m_value;
        }
        
        public static IsisAdminState getByValue(Integer value) {
            return m_valueMap.get(value);
        }

    }
    public final static String ISIS_SYS_ID_ALIAS = "isisSysID";
    public final static String ISIS_SYS_ID_OID = ".1.3.6.1.2.1.138.1.1.1.3";
    
    public final static String ISIS_SYS_ADMIN_STATE_ALIAS    = "isisSysAdminState";
    public final static String ISIS_SYS_ADMIN_STATE_OID = ".1.3.6.1.2.1.138.1.1.1.8";
    
    
    public static NamedSnmpVar[] ms_elemList = null;
    
    static {
        ms_elemList = new NamedSnmpVar[2];
        int ndx = 0;

        /**
         *   isisSysID OBJECT-TYPE
         *      SYNTAX IsisSystemID
         *        MAX-ACCESS read-create
         *        STATUS current
         *        DESCRIPTION
         *            "The ID for this Intermediate System.
         *             This value is appended to each of the
         *             area addresses to form the Network Entity Titles.
         *             The derivation of a value for this object is
         *             implementation specific.  Some implementations may
         *             automatically assign values and not permit an
         *             SNMP write, while others may require the value
         *             to be set manually.
         *
         *             Configured values MUST survive an agent reboot."
         *        REFERENCE "{ISIS.aoi systemId (119)}"
         *    ::= { isisSysObject 3 }
         */
        ms_elemList[ndx++] = new NamedSnmpVar(NamedSnmpVar.SNMPOCTETSTRING,ISIS_SYS_ID_ALIAS,ISIS_SYS_ID_OID);
        
        /**
         * isisSysAdminState OBJECT-TYPE
         *        SYNTAX IsisAdminState
         *        MAX-ACCESS read-create
         *        STATUS current
         *        DESCRIPTION
         *            "The administrative state of this Intermediate
         *             System.  Setting this object to the value 'on'
         *             when its current value is 'off' enables
         *             the Intermediate System.
         *
         *             Configured values MUST survive an agent reboot."
         *        DEFVAL { off }
         *    ::= { isisSysObject 8 } 
         */
        ms_elemList[ndx++] = new NamedSnmpVar(NamedSnmpVar.SNMPINT32,ISIS_SYS_ADMIN_STATE_ALIAS,ISIS_SYS_ADMIN_STATE_OID);
    }
    
    public static final String ISIS_SYS_OBJ_OID = ".1.3.6.1.2.1.138.1.1.1";

    private SnmpStore m_store;
    private InetAddress m_address;
    
    public IsIsSystemObjectGroup(InetAddress address) {
        super(NamedSnmpVar.getTrackersFor(ms_elemList));
        m_address = address;
        m_store = new SnmpStore(ms_elemList);
    }
    
    public IsisAdminState getIsisSysAdminState() {
        return IsisAdminState.getByValue(m_store.getInt32(ISIS_SYS_ADMIN_STATE_ALIAS));
        
    }
    
    public String getIsisSysId() {
        return m_store.getHexString(ISIS_SYS_ID_ALIAS);
    }
    
    
    /** {@inheritDoc} */
    @Override
    protected void storeResult(SnmpResult res) {
        m_store.storeResult(res);
    }

    /** {@inheritDoc} */
    @Override
    protected void reportGenErr(String msg) {
        LOG.warn("Error retrieving lldpLocalGroup from {}. {}", m_address, msg);
    }

    /** {@inheritDoc} */
    @Override
    protected void reportNoSuchNameErr(String msg) {
        LOG.info("Error retrieving lldpLocalGroup from {}. {}", m_address, msg);
    }


}
