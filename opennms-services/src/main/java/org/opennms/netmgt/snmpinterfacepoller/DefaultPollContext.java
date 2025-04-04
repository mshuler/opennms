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
package org.opennms.netmgt.snmpinterfacepoller;

import java.util.Date;
import java.util.List;

import org.opennms.core.criteria.Alias.JoinType;
import org.opennms.core.criteria.CriteriaBuilder;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.dao.api.IpInterfaceDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SnmpInterfaceDao;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.opennms.netmgt.model.PrimaryType;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.netmgt.snmp.proxy.LocationAwareSnmpClient;
import org.opennms.netmgt.snmpinterfacepoller.pollable.PollContext;
import org.opennms.netmgt.xml.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Represents a DefaultPollContext
 *
 * @author <a href="mailto:antonio@opennms.it">Antonio Russo</a>
 * @version $Id: $
 */
public class DefaultPollContext implements PollContext {
    
    
    private static final Logger LOG = LoggerFactory.getLogger(DefaultPollContext.class);
    
    private volatile EventIpcManager m_eventManager;
    private volatile String m_name;
    private volatile String m_localHostName;

    @Autowired
    private SnmpInterfaceDao m_snmpInterfaceDao;

    @Autowired
    private IpInterfaceDao m_ipInterfaceDao;

    @Autowired
    private NodeDao m_nodeDao;

    @Autowired
    private LocationAwareSnmpClient locationAwareSnmpClient;

    private String m_serviceName = "SNMP";

    /**
     * <p>getIpInterfaceDao</p>
     *
     * @return a {@link org.opennms.netmgt.dao.api.IpInterfaceDao} object.
     */
    public IpInterfaceDao getIpInterfaceDao() {
        return m_ipInterfaceDao;
    }

    public void setIpInterfaceDao(IpInterfaceDao ipInterfaceDao) {
        this.m_ipInterfaceDao = ipInterfaceDao;
    }


    /**
     * <p>getSnmpInterfaceDao</p>
     *
     * @return a {@link org.opennms.netmgt.dao.api.SnmpInterfaceDao} object.
     */
    public SnmpInterfaceDao getSnmpInterfaceDao() {
        return m_snmpInterfaceDao;
    }

    public void setSnmpInterfaceDao(SnmpInterfaceDao snmpInterfaceDao) {
        this.m_snmpInterfaceDao = snmpInterfaceDao;
    }

    public NodeDao getNodeDao() {
        return m_nodeDao;
    }

    public void setNodeDao(NodeDao nodeDao) {
        this.m_nodeDao = nodeDao;
    }

    /**
     * <p>getEventManager</p>
     *
     * @return a {@link org.opennms.netmgt.events.api.EventIpcManager} object.
     */
    public EventIpcManager getEventManager() {
        return m_eventManager;
    }
    
    /**
     * <p>setEventManager</p>
     *
     * @param eventManager a {@link org.opennms.netmgt.events.api.EventIpcManager} object.
     */
    public void setEventManager(EventIpcManager eventManager) {
        m_eventManager = eventManager;
    }
    
    /**
     * <p>setLocalHostName</p>
     *
     * @param localHostName a {@link java.lang.String} object.
     */
    public void setLocalHostName(String localHostName) {
        m_localHostName = localHostName;
    }
    
    /**
     * <p>getLocalHostName</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getLocalHostName() {
        return m_localHostName;
    }

    /**
     * <p>getName</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getName() {
        return m_name;
    }

    /**
     * <p>setName</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public void setName(String name) {
        m_name = name;
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.poller.pollables.PollContext#getCriticalServiceName()
     */
    /**
     * <p>getServiceName</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getServiceName() {
        return m_serviceName;
    }
    
    /** {@inheritDoc} */
    @Override
    public void setServiceName(String serviceName) {
        m_serviceName=serviceName;
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.poller.pollables.PollContext#sendEvent(org.opennms.netmgt.xml.event.Event)
     */
    /** {@inheritDoc} */
    @Override
    public void sendEvent(Event event) {
        getEventManager().sendNow(event);
    }

    private Logger log() {
        return LOG;
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.poller.pollables.PollContext#createEvent(java.lang.String, int, java.net.InetAddress, java.lang.String, java.util.Date)
     */
    /** {@inheritDoc} */
    @Override
    public Event createEvent(final String uei, final int nodeId, final String addr, final String netMask, final Date date, final OnmsSnmpInterface snmpinterface) {
        
        log().debug("createEvent: uei = " + uei + " nodeid = " + nodeId + " date = " + date);
        
        EventBuilder bldr = new EventBuilder(uei, this.getName(), date);
        bldr.setNodeid(nodeId);
        if (addr != null) {
            bldr.setInterface(InetAddressUtils.addr(addr));
        }
        if (netMask != null) {
            bldr.addParam(EventConstants.PARM_SNMP_INTERFACE_MASK, InetAddressUtils.normalize(netMask));
        }
        bldr.setService(getServiceName());

        bldr.setHost(this.getLocalHostName());
        bldr.setField("ifIndex", snmpinterface.getIfIndex().toString());

        bldr.addParam(EventConstants.PARM_SNMP_INTERFACE_IFINDEX, snmpinterface.getIfIndex().toString());

        if (snmpinterface.getIfName() != null) bldr.addParam(EventConstants.PARM_SNMP_INTERFACE_NAME, snmpinterface.getIfName());
        if (snmpinterface.getIfDescr() != null) bldr.addParam(EventConstants.PARM_SNMP_INTERFACE_DESC, snmpinterface.getIfDescr());
        if (snmpinterface.getIfAlias() != null) bldr.addParam(EventConstants.PARM_SNMP_INTERFACE_ALIAS, snmpinterface.getIfAlias());
        
        return bldr.getEvent();
    }

    /** {@inheritDoc} */
    @Override
    public List<OnmsSnmpInterface> get(int nodeId, String criteria) {
        CriteriaBuilder builder = new CriteriaBuilder(OnmsSnmpInterface.class);
        builder.sql(criteria).eq("node.id", nodeId).eq("poll", "P");
        builder.alias("ipInterfaces", "ipInterface", JoinType.LEFT_JOIN);
        return getSnmpInterfaceDao().findMatching(builder.toCriteria());

    }

    /** {@inheritDoc} */
    @Override
    public void update(OnmsSnmpInterface snmpinterface) {
    	OnmsSnmpInterface dbSnmpInterface = getSnmpInterfaceDao().findByNodeIdAndIfIndex(snmpinterface.getNode().getId(), snmpinterface.getIfIndex());
    	if (dbSnmpInterface == null)  {
        	log().debug("updating SnmpInterface: no interface found on db for: " + snmpinterface.toString());
    	} else {
    		dbSnmpInterface.setIfOperStatus(snmpinterface.getIfOperStatus());
    		dbSnmpInterface.setIfAdminStatus(snmpinterface.getIfAdminStatus());
    		dbSnmpInterface.setLastSnmpPoll(snmpinterface.getLastSnmpPoll());
            dbSnmpInterface.setPoll(snmpinterface.getPoll());
    		log().debug("updating SnmpInterface: " + dbSnmpInterface.toString());
    		getSnmpInterfaceDao().update(dbSnmpInterface);
    	}
    }

	@Override
	public List<OnmsIpInterface> getPollableNodesByIp(String ipaddr) {
                CriteriaBuilder builder = new CriteriaBuilder(OnmsIpInterface.class);
                builder.eq("ipAddress", InetAddressUtils.addr(ipaddr)).eq("snmpPrimary", PrimaryType.PRIMARY.getCharCode()).eq("isManaged", "M");
		return getIpInterfaceDao().findMatching(builder.toCriteria());
	}

	@Override
	public List<OnmsIpInterface> getPollableNodes() {
                CriteriaBuilder builder = new CriteriaBuilder(OnmsIpInterface.class);
                builder.eq("snmpPrimary", PrimaryType.PRIMARY.getCharCode()).eq("isManaged", "M");
		return getIpInterfaceDao().findMatching(builder.toCriteria());
	}

    @Override
    public String getLocation(Integer nodeId) {
        return m_nodeDao.getLocationForId(nodeId);
    }

    public LocationAwareSnmpClient getLocationAwareSnmpClient() {
        return locationAwareSnmpClient;
    }

    public void setLocationAwareSnmpClient(LocationAwareSnmpClient locationAwareSnmpClient) {
        this.locationAwareSnmpClient = locationAwareSnmpClient;
    }

}
