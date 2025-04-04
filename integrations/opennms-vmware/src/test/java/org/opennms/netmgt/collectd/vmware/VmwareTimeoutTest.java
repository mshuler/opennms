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
package org.opennms.netmgt.collectd.vmware;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Set;

import javax.net.ssl.TrustManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opennms.netmgt.collectd.VmwareCimCollector;
import org.opennms.netmgt.collectd.VmwareCollector;
import org.opennms.netmgt.collection.api.CollectionAgent;
import org.opennms.netmgt.config.vmware.VmwareServer;
import org.opennms.netmgt.config.vmware.cim.VmwareCimCollection;
import org.opennms.netmgt.config.vmware.cim.VmwareCimGroup;
import org.opennms.netmgt.config.vmware.vijava.VmwareCollection;
import org.opennms.netmgt.config.vmware.vijava.VmwareGroup;
import org.opennms.netmgt.model.ResourcePath;
import org.opennms.protocols.vmware.ServiceInstancePool;
import org.opennms.protocols.vmware.ServiceInstancePoolEntry;
import org.opennms.protocols.vmware.VmwareViJavaAccess;

import com.vmware.vim25.PerfEntityMetricBase;
import com.vmware.vim25.PerfProviderSummary;
import com.vmware.vim25.PerfQuerySpec;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.VimPortType;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.PerformanceManager;
import com.vmware.vim25.mo.ServerConnection;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.ws.Client;
import com.vmware.vim25.ws.WSClient;

public class VmwareTimeoutTest {

    final CollectionAgent collectionAgent = new CollectionAgent() {
        @Override
        public InetAddress getAddress() {
            return null;
        }

        @Override
        public Set<String> getAttributeNames() {
            return null;
        }

        @Override
        public <V> V getAttribute(String property) {
            return null;
        }

        @Override
        public Object setAttribute(String property, Object value) {
            return null;
        }

        @Override
        public Boolean isStoreByForeignSource() {
            return null;
        }

        @Override
        public String getHostAddress() {
            return null;
        }

        @Override
        public int getNodeId() {
            return 0;
        }

        @Override
        public String getNodeLabel() {
            return null;
        }

        @Override
        public String getForeignSource() {
            return null;
        }

        @Override
        public String getForeignId() {
            return null;
        }

        @Override
        public String getLocationName() {
            return null;
        }

        @Override
        public ResourcePath getStorageResourcePath() {
            return null;
        }

        @Override
        public long getSavedSysUpTime() {
            return 0;
        }

        @Override
        public void setSavedSysUpTime(long sysUpTime) {

        }
    };

    private class DummyServiceInstance extends ServiceInstance {

        public int dummyReadTimeout;
        public int dummyConnectTimeout;

        public DummyServiceInstance(String host, String username, String password) throws RemoteException, MalformedURLException {
            super(new URL("https://" + host + "/sdk"), username, password);
        }

        @Override
        protected void constructServiceInstance(URL url, String username, String password, boolean ignoreCert, String namespace, int connectTimeout, int readTimeout, TrustManager trustManager) throws RemoteException, MalformedURLException {
        }

        @Override
        protected void constructServiceInstance(URL url, String sessionStr, boolean ignoreCert, String namespace, int connectTimeout, int readTimeout, TrustManager trustManager) throws RemoteException, MalformedURLException {
        }

        @Override
        public ServerConnection getServerConnection() {
            return new ServerConnection(null, new VimPortType("http://hostname/sdk", null) {
                @Override
                public Client getWsc() {
                    try {
                        return new WSClient("http://hostname/sdk", false, null) {
                            @Override
                            public void setConnectTimeout(int timeoutMilliSec) {
                                dummyConnectTimeout = timeoutMilliSec;
                            }

                            @Override
                            public void setReadTimeout(int timeoutMilliSec) {
                                dummyReadTimeout = timeoutMilliSec;
                            }
                        };
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }, this);
        }

        @Override
        public PerformanceManager getPerformanceManager() {
            return new PerformanceManager(null, null) {
                @Override
                public PerfEntityMetricBase[] queryPerf(PerfQuerySpec[] querySpec) throws RuntimeFault, RemoteException {
                    return new PerfEntityMetricBase[0];
                }

                @Override
                public PerfProviderSummary queryPerfProviderSummary(ManagedEntity entity) throws RuntimeFault, RemoteException {
                    return new PerfProviderSummary() {
                        @Override
                        public Integer getRefreshRate() {
                            return 0;
                        }
                    };
                }
            };
        }
    }

    private DummyServiceInstance dummyServiceInstance;

    @Before
    public void setup() throws Exception {
        dummyServiceInstance = new DummyServiceInstance("hostname", "username", "password");
        VmwareViJavaAccess.setServiceInstancePool(new ServiceInstancePool() {
            @Override
            public ServiceInstance retain(String host, String username, String password, int timeout) throws MalformedURLException, RemoteException {
                ServiceInstancePoolEntry.setTimeout(dummyServiceInstance, timeout);
                return dummyServiceInstance;
            }
        });
    }

    @Test
    public void testVmwareCollectorDefault() throws Exception {
        final VmwareServer vmwareServer = new VmwareServer();
        vmwareServer.setHostname("hostname");
        vmwareServer.setUsername("username");
        vmwareServer.setPassword("password");

        final VmwareCollection vmwareCollection = new VmwareCollection();
        vmwareCollection.addVmwareGroup(new VmwareGroup());
        final HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("vmwareServer", vmwareServer);
        parameters.put("vmwareCollection", vmwareCollection);

        try {
            new VmwareCollector().collect(collectionAgent, parameters);
        } catch (NullPointerException e) {
            // ignore NPE
        }

        Assert.assertEquals(VmwareViJavaAccess.DEFAULT_TIMEOUT, dummyServiceInstance.dummyReadTimeout);
        Assert.assertEquals(VmwareViJavaAccess.DEFAULT_TIMEOUT, dummyServiceInstance.dummyConnectTimeout);
    }

    @Test
    public void testVmwareCollectorDefaultWithCutomTimeout() throws Exception {
        final VmwareServer vmwareServer = new VmwareServer();
        vmwareServer.setHostname("hostname");
        vmwareServer.setUsername("username");
        vmwareServer.setPassword("password");

        final VmwareCollection vmwareCollection = new VmwareCollection();
        vmwareCollection.addVmwareGroup(new VmwareGroup());
        final HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("vmwareServer", vmwareServer);
        parameters.put("vmwareCollection", vmwareCollection);
        parameters.put("timeout", new Integer(1500));

        try {
            new VmwareCollector().collect(collectionAgent, parameters);
        } catch (NullPointerException e) {
            // ignore NPE
        }

        Assert.assertEquals(1500, dummyServiceInstance.dummyReadTimeout);
        Assert.assertEquals(1500, dummyServiceInstance.dummyConnectTimeout);
    }

    @Test
    public void testVmwareCimCollectorDefault() throws Exception {
        final VmwareServer vmwareServer = new VmwareServer();
        vmwareServer.setHostname("hostname");
        vmwareServer.setUsername("username");
        vmwareServer.setPassword("password");

        final VmwareCimCollection vmwareCimCollection = new VmwareCimCollection();
        vmwareCimCollection.addVmwareCimGroup(new VmwareCimGroup());
        final HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("vmwareServer", vmwareServer);
        parameters.put("vmwareCollection", vmwareCimCollection);

        try {
            new VmwareCimCollector().collect(collectionAgent, parameters);
        } catch (NullPointerException e) {
            // ignore NPE
        }

        Assert.assertEquals(VmwareViJavaAccess.DEFAULT_TIMEOUT, dummyServiceInstance.dummyReadTimeout);
        Assert.assertEquals(VmwareViJavaAccess.DEFAULT_TIMEOUT, dummyServiceInstance.dummyConnectTimeout);
    }

    @Test
    public void testVmwareCimCollectorDefaultWithCutomTimeout() throws Exception {
        final VmwareServer vmwareServer = new VmwareServer();
        vmwareServer.setHostname("hostname");
        vmwareServer.setUsername("username");
        vmwareServer.setPassword("password");

        final VmwareCimCollection vmwareCimCollection = new VmwareCimCollection();
        vmwareCimCollection.addVmwareCimGroup(new VmwareCimGroup());
        final HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("vmwareServer", vmwareServer);
        parameters.put("vmwareCollection", vmwareCimCollection);
        parameters.put("timeout", new Integer(1500));

        try {
            new VmwareCimCollector().collect(collectionAgent, parameters);
        } catch (NullPointerException e) {
            // ignore NPE
        }

        Assert.assertEquals(1500, dummyServiceInstance.dummyReadTimeout);
        Assert.assertEquals(1500, dummyServiceInstance.dummyConnectTimeout);
    }
}
