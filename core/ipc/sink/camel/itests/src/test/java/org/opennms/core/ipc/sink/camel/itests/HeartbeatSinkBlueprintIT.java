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
package org.opennms.core.ipc.sink.camel.itests;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.hamcrest.Matchers.equalTo;

import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Component;
import org.apache.camel.util.KeyValueHolder;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.ipc.sink.api.MessageConsumer;
import org.opennms.core.ipc.sink.api.MessageConsumerManager;
import org.opennms.core.ipc.sink.api.MessageDispatcherFactory;
import org.opennms.core.ipc.sink.api.SinkModule;
import org.opennms.core.ipc.sink.api.SyncDispatcher;
import org.opennms.core.ipc.sink.camel.itests.HeartbeatSinkPerfIT.HeartbeatGenerator;
import org.opennms.core.ipc.sink.camel.itests.heartbeat.Heartbeat;
import org.opennms.core.ipc.sink.camel.itests.heartbeat.HeartbeatModule;
import org.opennms.core.ipc.sink.common.ThreadLockingMessageConsumer;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.activemq.ActiveMQBroker;
import org.opennms.core.test.camel.CamelBlueprintTest;
import org.opennms.distributed.core.api.MinionIdentity;
import org.opennms.distributed.core.api.SystemType;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations={
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-mockDao.xml",
        "classpath:/META-INF/opennms/applicationContext-proxy-snmp.xml",
        "classpath:/META-INF/opennms/applicationContext-queuingservice-mq-vm.xml",
        "classpath:/META-INF/opennms/applicationContext-ipc-sink-camel-server.xml",
        "classpath:/META-INF/opennms/applicationContext-ipc-sink-camel-client.xml",
        "classpath:/META-INF/opennms/applicationContext-tracer-registry.xml",
        "classpath:/META-INF/opennms/applicationContext-opennms-identity.xml"
})
@JUnitConfigurationEnvironment
@org.springframework.test.annotation.IfProfileValue(name="runFlappers", value="true")
public class HeartbeatSinkBlueprintIT extends CamelBlueprintTest {

    private static final String REMOTE_LOCATION_NAME = "remote";

    @ClassRule
    public static ActiveMQBroker broker = new ActiveMQBroker();

    @Autowired
    @Qualifier("queuingservice")
    private Component queuingservice;

    @Autowired
    private MessageDispatcherFactory localMessageDispatcherFactory;

    @Autowired
    private MessageConsumerManager consumerManager;
    
    @SuppressWarnings( "rawtypes" )
    @Override
    protected void addServicesOnStartup(Map<String, KeyValueHolder<Object, Dictionary>> services) {
        services.put(MinionIdentity.class.getName(),
                new KeyValueHolder<Object, Dictionary>(new MinionIdentity() {
                    @Override
                    public String getId() {
                        return "0";
                    }
                    @Override
                    public String getLocation() {
                        return REMOTE_LOCATION_NAME;
                    }
                    @Override
                    public String getType() {
                        return SystemType.Minion.name();
                    }
                }, new Properties()));

        Properties props = new Properties();
        props.setProperty("alias", "opennms.broker");
        services.put(Component.class.getName(), new KeyValueHolder<Object, Dictionary>(queuingservice, props));
    }

    @Override
    protected String getBlueprintDescriptor() {
        return "classpath:/OSGI-INF/blueprint/blueprint-ipc-client.xml";
    }

    @Test(timeout=60000)
    public void canProduceAndConsumerMessages() throws Exception {
        HeartbeatModule module = new HeartbeatModule();

        AtomicInteger heartbeatCount = new AtomicInteger();
        MessageConsumer<Heartbeat, Heartbeat> consumer = new MessageConsumer<Heartbeat, Heartbeat>() {
            @Override
            public SinkModule<Heartbeat, Heartbeat> getModule() {
                return module;
            }

            @Override
            public void handleMessage(Heartbeat heartbeat) {
                heartbeatCount.incrementAndGet();
            }
        };
        consumerManager.registerConsumer(consumer);

        SyncDispatcher<Heartbeat> localDispatcher = localMessageDispatcherFactory.createSyncDispatcher(module);
        localDispatcher.send(new Heartbeat());
        await().atMost(1, MINUTES).until(() -> heartbeatCount.get(), equalTo(1));

        MessageDispatcherFactory remoteMessageDispatcherFactory = context.getRegistry().lookupByNameAndType("camelRemoteMessageDispatcherFactory", MessageDispatcherFactory.class);
        SyncDispatcher<Heartbeat> remoteDispatcher = remoteMessageDispatcherFactory.createSyncDispatcher(module);
        remoteDispatcher.send(new Heartbeat());
        await().atMost(1, MINUTES).until(() -> heartbeatCount.get(), equalTo(2));

        consumerManager.unregisterConsumer(consumer);
    }

    @Test(timeout=60000)
    public void canConsumeMessagesInParallel() throws Exception {
        final int NUM_CONSUMER_THREADS = 7;

        final HeartbeatModule parallelHeartbeatModule = new HeartbeatModule() {
            @Override
            public int getNumConsumerThreads() {
                return NUM_CONSUMER_THREADS;
            }
        };

        ThreadLockingMessageConsumer<Heartbeat, Heartbeat> consumer =
                new ThreadLockingMessageConsumer<>(parallelHeartbeatModule);

        CompletableFuture<Integer> future = consumer.waitForThreads(NUM_CONSUMER_THREADS);
        consumerManager.registerConsumer(consumer);

        MessageDispatcherFactory remoteMessageDispatcherFactory = context.getRegistry().lookupByNameAndType("camelRemoteMessageDispatcherFactory", MessageDispatcherFactory.class);
        SyncDispatcher<Heartbeat> dispatcher = remoteMessageDispatcherFactory.createSyncDispatcher(HeartbeatModule.INSTANCE);

        HeartbeatGenerator generator = new HeartbeatGenerator(dispatcher, 100.0);
        generator.start();

        // Wait until we have NUM_CONSUMER_THREADS locked
        future.get();

        // Take a snooze
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));

        // Verify that there aren't more than NUM_CONSUMER_THREADS waiting
        assertEquals(0, consumer.getNumExtraThreadsWaiting());

        generator.stop();

        // This doesn't work because the consumer seems to leave NUM_CONSUMER_THREADS
        // messages in-flight if it's unregistered before the context shuts down
        // consumerManager.unregisterConsumer(consumer);
    }
}
