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
package org.opennms.features.kafka.producer;

import com.google.common.base.Strings;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.db.MockDatabase;
import org.opennms.core.test.db.TemporaryDatabaseAware;
import org.opennms.core.test.db.annotations.JUnitTemporaryDatabase;
import org.opennms.core.test.kafka.JUnitKafkaServer;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.features.kafka.producer.datasync.KafkaAlarmDataSync;
import org.opennms.features.kafka.producer.model.CollectionSetProtos;
import org.opennms.features.kafka.producer.model.OpennmsModelProtos;
import org.opennms.features.situationfeedback.api.AlarmFeedback;
import org.opennms.netmgt.alarmd.AlarmLifecycleListenerManager;
import org.opennms.netmgt.dao.DatabasePopulator;
import org.opennms.netmgt.dao.DatabasePopulator.DaoSupport;
import org.opennms.netmgt.dao.api.AlarmDao;
import org.opennms.netmgt.dao.api.HwEntityDao;
import org.opennms.netmgt.dao.mock.MockEventIpcManager;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.mock.MockEventUtil;
import org.opennms.netmgt.model.OnmsAlarm;
import org.opennms.netmgt.model.OnmsHwEntity;
import org.opennms.netmgt.model.OnmsHwEntityAlias;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsSeverity;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyDao;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies events/alarms/nodes forwarded to Kafka.
 *
 * @author cgorantla
 * @author jwhite
 */
@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:/META-INF/opennms/applicationContext-dao.xml",
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-commonConfigs.xml",
        "classpath:/META-INF/opennms/applicationContext-eventUtil.xml",
        "classpath:/META-INF/opennms/applicationContext-minimal-conf.xml",
        "classpath:/META-INF/opennms/applicationContext-mockDao.xml",
        "classpath:/META-INF/opennms/applicationContext-daemon.xml",
        "classpath:/META-INF/opennms/mockEventIpcManager.xml",
        "classpath:/META-INF/opennms/applicationContext-alarmd.xml",
        "classpath*:/META-INF/opennms/component-dao.xml",
        "classpath:/applicationContext-test-kafka-producer.xml" })
@JUnitConfigurationEnvironment
@JUnitTemporaryDatabase(dirtiesContext = false, tempDbClass = MockDatabase.class, reuseDatabase = false)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class KafkaForwarderIT implements TemporaryDatabaseAware<MockDatabase> {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaForwarderIT.class);

    private static final String EVENT_TOPIC_NAME = "events";
    private static final String ALARM_TOPIC_NAME = "test-alarms";
    private static final String NODE_TOPIC_NAME = "test-nodes";
    private static final String METRIC_TOPIC_NAME = "test-metrics";
    private static final String ALARM_FEEDBACK_TOPIC_NAME = "test-alarm-feedback";

    private int _id = 1;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public JUnitKafkaServer kafkaServer = new JUnitKafkaServer(tempFolder);

    @Autowired
    private MockEventIpcManager eventdIpcMgr;

    @Autowired
    private ProtobufMapper protobufMapper;

    @Autowired
    private NodeCache nodeCache;

    @Autowired
    private AlarmLifecycleListenerManager alarmLifecycleListenerManager;

    @Autowired
    private AlarmDao alarmDao;

    @Autowired
    private DatabasePopulator databasePopulator;
    
    @Autowired
    private HwEntityDao hwEntityDao;

    private MockDatabase mockDatabase;

    private OpennmsKafkaProducer kafkaProducer;

    private KafkaAlarmDataSync kafkaAlarmaDataStore;

    private ExecutorService executor;

    private KafkaMessageConsumerRunner kafkaConsumer;

    @Autowired
    private OnmsTopologyDao onmsTopologyDao;
    @Before
    public void setUp() throws IOException {
        File karaf = tempFolder.newFolder("karaf");
        System.setProperty("karaf.data", karaf.getAbsolutePath());

        File data = tempFolder.newFolder("data");

        eventdIpcMgr.setEventWriter(mockDatabase);

        databasePopulator.addExtension(new DatabasePopulator.Extension<HwEntityDao>() {

            @Override
            public DaoSupport<HwEntityDao> getDaoSupport() {
                return new DaoSupport<HwEntityDao>(HwEntityDao.class, hwEntityDao);
            }

            @Override
            public void onPopulate(DatabasePopulator populator, HwEntityDao dao) {
                OnmsNode node = databasePopulator.getNode1();
                OnmsHwEntity port = getHwEntityPort(node);
                dao.save(port);
                OnmsHwEntity container = getHwEntityContainer(node);
                container.addChildEntity(port);
                dao.save(container);
                OnmsHwEntity module = getHwEntityModule(node);
                module.addChildEntity(container);
                dao.save(module);
                OnmsHwEntity powerSupply = getHwEntityPowerSupply(node);
                dao.save(powerSupply);
                OnmsHwEntity chassis = getHwEntityChassis(node);
                chassis.addChildEntity(module);
                chassis.addChildEntity(powerSupply);
                dao.save(chassis);
                dao.flush();
            }

            @Override
            public void onShutdown(DatabasePopulator populator, HwEntityDao dao) {
                for (OnmsHwEntity entity : dao.findAll()) {
                    dao.delete(entity);
                }
            }
        });
        
        databasePopulator.populateDatabase();

        Hashtable<String, Object> producerConfig = new Hashtable<>();
        producerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, KafkaForwarderIT.class.getCanonicalName());
        producerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServer.getKafkaConnectString());
        producerConfig.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 3000);
        producerConfig.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        producerConfig.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
        ConfigurationAdmin configAdmin = mock(ConfigurationAdmin.class, RETURNS_DEEP_STUBS);
        Hashtable<String, Object> streamsConfig = new Hashtable<>();
        streamsConfig.put(StreamsConfig.STATE_DIR_CONFIG, data.getAbsolutePath());
        streamsConfig.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
        streamsConfig.put(StreamsConfig.METADATA_MAX_AGE_CONFIG, 1000);
        when(configAdmin.getConfiguration(OpennmsKafkaProducer.KAFKA_CLIENT_PID).getProperties()).thenReturn(producerConfig);
        when(configAdmin.getConfiguration(KafkaAlarmDataSync.KAFKA_STREAMS_PID).getProperties()).thenReturn(streamsConfig);

        kafkaProducer = new OpennmsKafkaProducer(protobufMapper, nodeCache, configAdmin, eventdIpcMgr, onmsTopologyDao, 5);
        kafkaProducer.setEventTopic(EVENT_TOPIC_NAME);
        // Don't forward newSuspect events
        kafkaProducer.setEventFilter("!getUei().equals(\"" + EventConstants.NEW_SUSPECT_INTERFACE_EVENT_UEI + "\")");
        kafkaProducer.setAlarmTopic(ALARM_TOPIC_NAME);
        kafkaProducer.setAlarmFeedbackTopic(ALARM_FEEDBACK_TOPIC_NAME);
        // No alarm filtering
        kafkaProducer.setAlarmFilter(null);
        kafkaProducer.setNodeTopic(NODE_TOPIC_NAME);
        kafkaProducer.init();

        kafkaAlarmaDataStore = new KafkaAlarmDataSync(configAdmin, kafkaProducer, protobufMapper);
        kafkaAlarmaDataStore.setAlarmTopic(ALARM_TOPIC_NAME);
        kafkaAlarmaDataStore.setAlarmSync(true);
        kafkaAlarmaDataStore.init();
        kafkaProducer.setDataSync(kafkaAlarmaDataStore);

        alarmLifecycleListenerManager.onListenerRegistered(kafkaProducer, Collections.emptyMap());
    }

    @Test
    @JUnitTemporaryDatabase(dirtiesContext=true, tempDbClass=MockDatabase.class, reuseDatabase=false)
    public void canProduceAndConsumeMessages() throws Exception {
        // Send a node down event (should be forwarded)
        eventdIpcMgr.sendNow(MockEventUtil.createNodeDownEventBuilder("test", databasePopulator.getNode1()).getEvent());
        // Create and trigger the associated alarm
        final OnmsAlarm alarm = nodeDownAlarmWithRelatedAlarm();
        final String alarmReductionKey = alarm.getReductionKey();
        {
            alarmDao.save(alarm);
            kafkaProducer.handleNewOrUpdatedAlarm(alarm);
        }

        // Send a unrelated newSuspect event (should not be forwarded)
        eventdIpcMgr.sendNow(MockEventUtil.createNewSuspectEventBuilder("test",
                EventConstants.NEW_SUSPECT_INTERFACE_EVENT_UEI, InetAddressUtils.str(InetAddressUtils.UNPINGABLE_ADDRESS))
                .getEvent());

        // Send a node up (should be forwarded)
        eventdIpcMgr.sendNow(MockEventUtil.createNodeUpEventBuilder("test", databasePopulator.getNode2()).getEvent());
        
        AlarmFeedback alarmFeedback = createTestalarmFeedback();
        kafkaProducer.handleAlarmFeedback(Collections.singletonList(alarmFeedback));

        if (!kafkaProducer.getEventForwardedLatch().await(1, TimeUnit.MINUTES)) {
            throw new Exception("No events were successfully forwarded in time!");
        }
        if (!kafkaProducer.getNodeForwardedLatch().await(1, TimeUnit.MINUTES)) {
            throw new Exception("No nodes were successfully forwarded in time!");
        }
        if (!kafkaProducer.getAlarmForwardedLatch().await(1, TimeUnit.MINUTES)) {
            throw new Exception("No alarm were successfully forwarded in time!");
        }
        if (!kafkaProducer.getAlarmFeedbackForwardedLatch().await(1, TimeUnit.MINUTES)) {
            throw new Exception("No alarm feedback was successfully forwarded in time!");
        }

        // Fire up the consumer
        kafkaConsumer = startConsumer();

        // Wait for the events to be consumed
        await().atMost(1, TimeUnit.MINUTES).until(this::getUeisForConsumedEvents, hasItems(
                EventConstants.NODE_DOWN_EVENT_UEI, EventConstants.NODE_UP_EVENT_UEI));
        assertThat(getUeisForConsumedEvents(), not(hasItem(EventConstants.NEW_SUSPECT_INTERFACE_EVENT_UEI)));
        // Wait for the nodes to be consumed
        await().atMost(1, TimeUnit.MINUTES).until(this::getIdsForConsumedNodes, hasItems(
                databasePopulator.getNode1().getId(), databasePopulator.getNode2().getId()));
        // Wait for the alarms to be consumed
        await().atMost(1, TimeUnit.MINUTES).until(() -> kafkaConsumer.getAlarmByReductionKey(alarmReductionKey), not(nullValue()));
        // Wait for alarm feedback to be consumed
        await().atMost(1, TimeUnit.MINUTES).until(() -> kafkaConsumer.getAlarmFeedback(), not(empty()));

        // Events, nodes and alarms were forwarded and consumed!

        // Verify the alarm feedback consumed
        OpennmsModelProtos.AlarmFeedback consumedAlarmFeedback = kafkaConsumer.getAlarmFeedback().get(0);
        assertThat(consumedAlarmFeedback.getSituationKey(), is(equalTo(alarmFeedback.getSituationKey())));
        
        // Ensure that we have some events with a fs:fid

        List<OpennmsModelProtos.Event> eventsWithFsAndFid = kafkaConsumer.getEvents().stream()
                .filter(e -> !Strings.isNullOrEmpty(e.getNodeCriteria().getForeignId())
                        && !Strings.isNullOrEmpty(e.getNodeCriteria().getForeignSource()))
                .collect(Collectors.toList());
        assertThat(eventsWithFsAndFid, hasSize(greaterThanOrEqualTo(2)));
        assertThat(eventsWithFsAndFid.get(0).getCreateTime(), greaterThan(0L));

        List<OpennmsModelProtos.Event> eventsWithNodeLabel = kafkaConsumer.getEvents().stream()
                .filter(e -> !Strings.isNullOrEmpty(e.getNodeCriteria().getNodeLabel()))
                .collect(Collectors.toList());
        List<OpennmsModelProtos.Event> eventsWithNodeLocation = kafkaConsumer.getEvents().stream()
                .filter(e -> !Strings.isNullOrEmpty(e.getNodeCriteria().getLocation()))
                .collect(Collectors.toList());
        List<OpennmsModelProtos.Event> eventsWithDistPoller = kafkaConsumer.getEvents().stream()
                .filter(e -> !Strings.isNullOrEmpty(e.getDistPoller()))
                .collect(Collectors.toList());
        assertThat(eventsWithNodeLabel, hasSize(greaterThanOrEqualTo(1)));
        assertThat(eventsWithNodeLocation, hasSize(greaterThanOrEqualTo(1)));
        assertThat(eventsWithDistPoller, hasSize(greaterThanOrEqualTo(1)));
        // Verify the consumed alarm object
        assertThat(kafkaConsumer.getAlarmByReductionKey(alarmReductionKey).getDescription(), equalTo("node down"));
        // Verify the consumed Node objects
        List<org.opennms.features.kafka.producer.model.OpennmsModelProtos.Node> nodes = kafkaConsumer.getNodes();
        LOG.debug("got nodes: {}", nodes);
        assertThat(nodes, hasSize(greaterThanOrEqualTo(2)));
        // Verify the first node has hwInventory DAG including the Port with a hwEntAlias
        assertThat(nodes.get(0), not(nullValue()));
        assertThat(nodes.get(0).getHwInventory(), not(nullValue()));
        assertThat(nodes.get(0).getHwInventory().getChildrenList().size(), equalTo(2));
        List<OpennmsModelProtos.HwEntity> hwEntityList = nodes.get(0).getHwInventory().getChildrenList();
        OpennmsModelProtos.HwEntity hwEntity = hwEntityList.stream().filter(entity -> entity.getChildrenCount() > 0).findFirst().get();
        assertThat(hwEntity.getChildren(0).getChildren(0).getEntHwAliasCount(), equalTo(1));
        assertThat(hwEntity.getChildren(0).getChildren(0).getEntHwAlias(0).getIndex(), equalTo(0));
        assertThat(hwEntity.getChildren(0).getChildren(0).getEntHwAlias(0).getOid(), equalTo(".1.3.6.1.2.1.2.2.1.1.10104"));

        // Now delete the alarm directly in the database
        alarmDao.delete(alarm);

        // Wait until the synchronization process kicks off and delete the alarm
        await().atMost(2, TimeUnit.MINUTES).until(() ->
                kafkaConsumer.getAlarmsByReductionKey().get(alarmReductionKey) == null);
    }

    @Test
    public void testProducerSuppression() throws Exception {
        kafkaProducer.setSuppressIncrementalAlarms(true);

        // Fire up the consumer
        kafkaConsumer = startConsumer();

        // Send an alarm
        final OnmsAlarm alarm = nodeDownAlarmWithRelatedAlarm();
        alarmDao.save(alarm);
        kafkaProducer.handleNewOrUpdatedAlarm(alarm);

        // Wait for callback to save incremental alarm metadata
        Thread.sleep(10000);

        // Increment the alarm and re-send
        alarm.setCounter(2);
        // Also update the related alarm
        alarm.getRelatedAlarms().iterator().next().setCounter(2);
        alarmDao.save(alarm);
        kafkaProducer.handleNewOrUpdatedAlarm(alarm);

        // One alarm should have been consumed
        await().atMost(1, TimeUnit.MINUTES).until(() -> !kafkaConsumer.getAlarms().isEmpty());
        // Sleep an additional 10 seconds to verify a second alarm was not consumed
        Thread.sleep(10000);
        assertEquals(1, kafkaConsumer.getAlarms().size());
    }

    @Test
    public void testSyncSuppression() {
        kafkaProducer.setSuppressIncrementalAlarms(true);

        // Send an alarm
        final OnmsAlarm alarm = nodeDownAlarmWithRelatedAlarm();
        alarmDao.save(alarm);
        kafkaProducer.handleNewOrUpdatedAlarm(alarm);

        // Increment the alarm and write directly to db
        alarm.setCounter(2);
        // Also update the related alarm
        alarm.getRelatedAlarms().iterator().next().setCounter(2);
        alarmDao.save(alarm);

        // Fire up the consumer
        kafkaConsumer = startConsumer();

        // Only one alarm should have been consumed
        try {
            // This is a bit tricky since we need to wait fairly long to verify that the sync suppressed the alarm
            await().atMost(2, TimeUnit.MINUTES).until(() -> kafkaConsumer.getAlarms().size() > 1);
            fail("Alarm was not suppressed!");
        } catch (Exception e) {
        }
    }
    
    @Test
    public void testSameAlarmIsNotSynced() {
        kafkaProducer.setSuppressIncrementalAlarms(false);

        // Send an alarm
        final OnmsAlarm alarm = nodeDownAlarmWithRelatedAlarm();
        alarmDao.save(alarm);
        kafkaProducer.handleNewOrUpdatedAlarm(alarm);

        // Fire up the consumer
        kafkaConsumer = startConsumer();

        await().atMost(1, TimeUnit.MINUTES)
                .ignoreExceptions()
                .pollDelay(5, TimeUnit.SECONDS)
                .until(() -> kafkaProducer.getDataSync().isReady());
        
        // Force an alarm sync
        kafkaProducer.handleAlarmSnapshot(Collections.singletonList(alarm));

        // Only the alarm explicitly sent should be consumed, there should not have been any alarms sync'd        
        try {            
            await().atMost(10, TimeUnit.SECONDS).until(() -> kafkaConsumer.getAlarms().size() > 1);
            fail("Same alarm was sync'd!");
        } catch (Exception e) {
        }
    }


    @Test
    public void testNotDroppingOfEventsWhenKafkaIsOffline() throws Exception {

        eventdIpcMgr.sendNow(MockEventUtil.createNodeDownEventBuilder("test", databasePopulator.getNode1()).getEvent());
        if (!kafkaProducer.getEventForwardedLatch().await(1, TimeUnit.MINUTES)) {
            throw new Exception("No events were successfully forwarded in time!");
        }
        kafkaConsumer = startConsumer();
        await().atMost(1, TimeUnit.MINUTES).until(this::getUeisForConsumedEvents, hasItems(
                EventConstants.NODE_DOWN_EVENT_UEI));
        kafkaServer.stopKafkaServer();
        eventdIpcMgr.sendNow(MockEventUtil.createNodeUpEventBuilder("test", databasePopulator.getNode2()).getEvent());
        // Sleep long enough to account for delivery.timeout.ms = 3 secs.
        Thread.sleep(10000);
        kafkaServer.startKafkaServer();
        await().atMost(1, TimeUnit.MINUTES).until(this::getUeisForConsumedEvents, hasItems(
                EventConstants.NODE_DOWN_EVENT_UEI, EventConstants.NODE_UP_EVENT_UEI));

    }

    @Test
    public void testNotDroppingOfAlarmsWhenKafkaIsOffline() throws Exception {
        // Fire up the consumer
        kafkaConsumer = startConsumer();

        kafkaServer.stopKafkaServer();

        // Send an alarm
        final OnmsAlarm alarm = nodeDownAlarm();
        alarmDao.save(alarm);
        kafkaProducer.handleNewOrUpdatedAlarm(alarm);

        Thread.sleep(10000);
        assertEquals(0, kafkaConsumer.getAlarms().size());

        kafkaServer.startKafkaServer();

        // Verify alarm is received
        await().pollDelay(5, TimeUnit.SECONDS).atMost(1, TimeUnit.MINUTES).until(() -> !kafkaConsumer.getAlarms().isEmpty());
    }

    private KafkaMessageConsumerRunner startConsumer() {
        executor = Executors.newSingleThreadExecutor();
        kafkaConsumer = new KafkaMessageConsumerRunner(kafkaServer.getKafkaConnectString());
        executor.execute(kafkaConsumer);
        return kafkaConsumer;
    }

    private OnmsAlarm nodeDownAlarmWithRelatedAlarm() {
        OnmsAlarm alarm = nodeDownAlarm();
        OnmsAlarm relatedAlarm = nodeDownAlarm();
        relatedAlarm.setId(_id++);
        alarm.addRelatedAlarm(relatedAlarm);        
        return alarm;
    }
    
    private OnmsAlarm nodeDownAlarm() {
        OnmsAlarm alarm = new OnmsAlarm();
        alarm.setId(_id++);
        alarm.setUei(EventConstants.NODE_DOWN_EVENT_UEI);
        alarm.setNode(databasePopulator.getNode1());
        alarm.setCounter(1);
        alarm.setDescription("node down");
        alarm.setAlarmType(1);
        alarm.setLogMsg("node down");
        alarm.setSeverity(OnmsSeverity.NORMAL);
        alarm.setReductionKey(String.format("%s:%d", EventConstants.NODE_DOWN_EVENT_UEI,
                databasePopulator.getNode1().getId()));
        return alarm;
    }

    private Set<String> getUeisForConsumedEvents() {
        return kafkaConsumer.getEvents().stream()
                .map(OpennmsModelProtos.Event::getUei)
                .collect(Collectors.toSet());
    }

    private Set<Integer> getIdsForConsumedNodes() {
        return kafkaConsumer.getNodes().stream()
                .filter(Objects::nonNull)
                .map(n -> (int)n.getId())
                .collect(Collectors.toSet());
    }

    public static class KafkaMessageConsumerRunner implements Runnable {
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private KafkaConsumer<String, byte[]> consumer;
        private String kafkaConnectString;
        private List<OpennmsModelProtos.Event> events = new ArrayList<>();
        private List<OpennmsModelProtos.Node> nodes = new ArrayList<>();
        private List<OpennmsModelProtos.Alarm> alarms = new ArrayList<>();
        private List<OpennmsModelProtos.AlarmFeedback> alarmFeedback = new ArrayList<>();
        private List<CollectionSetProtos.CollectionSet> collectionSetValues = new ArrayList<>();
        private Map<String, OpennmsModelProtos.Alarm> alarmsByReductionKey = new LinkedHashMap<>();
        private AtomicInteger numRecordsConsumed = new AtomicInteger(0);

        private AtomicInteger numOfMetricRecords = new AtomicInteger(0);

        public KafkaMessageConsumerRunner(String kafkaConnectString) {
            this.kafkaConnectString = Objects.requireNonNull(kafkaConnectString);
        }

        @Override
        public void run() {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConnectString);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, KafkaMessageConsumerRunner.class.getCanonicalName() + "-" + UUID.randomUUID().toString());
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.TRUE.toString());
            props.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, "1000");
            props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Arrays.asList(EVENT_TOPIC_NAME, NODE_TOPIC_NAME, ALARM_TOPIC_NAME, METRIC_TOPIC_NAME,
                    ALARM_FEEDBACK_TOPIC_NAME));

            while (!closed.get()) {
                ConsumerRecords<String, byte[]> records = consumer.poll(1000);
                for (ConsumerRecord<String, byte[]> record : records) {
                    try {
                        switch (record.topic()) {
                            case EVENT_TOPIC_NAME:
                                events.add(OpennmsModelProtos.Event.parseFrom(record.value()));
                                break;
                            case NODE_TOPIC_NAME:
                                nodes.add(record.value() != null ?
                                        OpennmsModelProtos.Node.parseFrom(record.value()) : null);
                                break;
                            case ALARM_TOPIC_NAME:
                                final OpennmsModelProtos.Alarm alarm = record.value() != null ?
                                        OpennmsModelProtos.Alarm.parseFrom(record.value()) : null;
                                alarms.add(alarm);
                                alarmsByReductionKey.put(record.key(), alarm);
                                break;
                            case METRIC_TOPIC_NAME :
                                collectionSetValues.add(CollectionSetProtos.CollectionSet.parseFrom(record.value()));
                                numOfMetricRecords.incrementAndGet();
                                break;
                            case ALARM_FEEDBACK_TOPIC_NAME:
                                final OpennmsModelProtos.AlarmFeedback alarmFeedbackRecord = record.value() != null ?
                                        OpennmsModelProtos.AlarmFeedback.parseFrom(record.value()) : null;
                                alarmFeedback.add(alarmFeedbackRecord);
                                break;
                        }
                        numRecordsConsumed.incrementAndGet();
                    } catch (Exception e) {
                        LOG.error("Failed to parse record: {}",  record, e);
                    }
                }
            }
            consumer.close(Duration.ofMinutes(1));
        }

        public AtomicInteger getNumRecordsConsumed() {
            return numRecordsConsumed;
        }

        public List<OpennmsModelProtos.Event> getEvents() {
            return events;
        }

        public List<OpennmsModelProtos.Node> getNodes() {
            return nodes;
        }

        public List<OpennmsModelProtos.Alarm> getAlarms() {
            return alarms;
        }

        public Map<String, OpennmsModelProtos.Alarm> getAlarmsByReductionKey() {
            return alarmsByReductionKey;
        }

        public OpennmsModelProtos.Alarm getAlarmByReductionKey(String reductionKey) {
            return alarmsByReductionKey.get(reductionKey);
        }
        
        public List<OpennmsModelProtos.AlarmFeedback> getAlarmFeedback() {
            return alarmFeedback;
        }

        public void shutdown() {
            closed.set(true);
        }

        public List<CollectionSetProtos.CollectionSet> getCollectionSetValues() {
            return collectionSetValues;
        }
        public void clearCollectionSetValues() {
            collectionSetValues.clear();
        }

        public AtomicInteger getNumOfMetricRecords (){
            return numOfMetricRecords;
        }

        public void clearNumofMetricRecords() {
            numOfMetricRecords.set(0);
        }

    }

    static OnmsHwEntity getHwEntityChassis(OnmsNode node) {
        final OnmsHwEntity chassis = new OnmsHwEntity();
        chassis.setNode(node);
        chassis.setEntPhysicalIndex(40);
        chassis.setEntPhysicalClass("chassis");
        chassis.setEntPhysicalDescr("ME-3400EG-2CS-A");
        chassis.setEntPhysicalFirmwareRev("12.2(60)EZ1");
        chassis.setEntPhysicalHardwareRev("V03");
        chassis.setEntPhysicalIsFRU(false);
        chassis.setEntPhysicalModelName("ME-3400EG-2CS-A");
        chassis.setEntPhysicalName("1");
        chassis.setEntPhysicalSerialNum("FOC1701V24Y");
        chassis.setEntPhysicalSoftwareRev("12.2(60)EZ1");
        chassis.setEntPhysicalVendorType(".1.3.6.1.4.1.9.12.3.1.3.760");
        return chassis;
    }
    static OnmsHwEntity getHwEntityPowerSupply(OnmsNode node) {
        final OnmsHwEntity powerSupply = new OnmsHwEntity();
        powerSupply.setNode(node);
        powerSupply.setEntPhysicalIndex(39);
        powerSupply.setEntPhysicalClass("powerSupply");
        powerSupply.setEntPhysicalDescr("ME-3400EG-2CS-A - Fan 0");
        powerSupply.setEntPhysicalIsFRU(false);
        powerSupply.setEntPhysicalModelName("ME-3400EG-2CS-A - Fan 0");
        powerSupply.setEntPhysicalVendorType(".1.3.6.1.4.1.9.12.3.1.7.81");
        return powerSupply;
    }
    static OnmsHwEntity getHwEntityModule(OnmsNode node) {
        final OnmsHwEntity module = new OnmsHwEntity();
        module.setNode(node);
        module.setEntPhysicalIndex(37);
        module.setEntPhysicalClass("module");
        module.setEntPhysicalDescr("ME-3400EG-2CS-A - Power Supply 0");
        module.setEntPhysicalIsFRU(false);
        module.setEntPhysicalModelName("ME-3400EG-2CS-A - Power Supply 0");
        module.setEntPhysicalSerialNum("LIT16490HHP");
        module.setEntPhysicalVendorType(".1.3.6.1.4.1.9.12.3.1.6.223");
        return module;
    }
    static OnmsHwEntity getHwEntityContainer(OnmsNode node) {
        OnmsHwEntity container = new OnmsHwEntity();
        container.setNode(node);
        container.setEntPhysicalIndex(36);
        container.setEntPhysicalClass("container");
        container.setEntPhysicalDescr("GigabitEthernet Container");
        container.setEntPhysicalIsFRU(false);
        container.setEntPhysicalName("GigabitEthernet0/4 Container");
        container.setEntPhysicalVendorType(".1.3.6.1.4.1.9.12.3.1.5.115");
        return container;
    }
    static OnmsHwEntity getHwEntityPort(OnmsNode node) {
        final OnmsHwEntity port = new OnmsHwEntity();
        port.setNode(node);
        port.setEntPhysicalIndex(35);
        port.setEntPhysicalAlias("10104");
        port.setEntPhysicalClass("port");
        port.setEntPhysicalDescr("1000BaseBX10-U SFP");
        port.setEntPhysicalHardwareRev("V01");
        port.setEntPhysicalIsFRU(true);
        port.setEntPhysicalModelName("GLC-BX-U");
        port.setEntPhysicalName("GigabitEthernet0/4");
        port.setEntPhysicalSerialNum("L03C2AC0179");
        port.setEntPhysicalVendorType(".1.3.6.1.4.1.9.12.3.1.10.253");
        OnmsHwEntityAlias onmsHwEntityAlias = new OnmsHwEntityAlias(0, ".1.3.6.1.2.1.2.2.1.1.10104");
        onmsHwEntityAlias.setHwEntity(port);
        port.setEntAliases(new TreeSet<>(Arrays.asList(onmsHwEntityAlias)));
        return port;
    }
    
    private AlarmFeedback createTestalarmFeedback() {
        return AlarmFeedback.newBuilder()
                .withSituationKey("test.situationKey")
                .withSituationFingerprint("test.fingerprint")
                .withAlarmKey("test.alarmKey")
                .withFeedbackType(AlarmFeedback.FeedbackType.FALSE_POSITIVE)
                .withReason("reason")
                .withUser("user")
                .build();
    }

    @After
    public void tearDown() throws Exception {
        if (kafkaAlarmaDataStore != null) {
            kafkaAlarmaDataStore.destroy();
        }
        if (kafkaProducer != null) {
            kafkaProducer.destroy();
        }
        if (kafkaConsumer != null) {
            alarmLifecycleListenerManager.onListenerUnregistered(kafkaProducer, Collections.emptyMap());
            kafkaConsumer.shutdown();
        }
        if (executor != null) {
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.MINUTES);
        }
        eventdIpcMgr.reset();
        alarmDao.findAll().forEach(alarm -> alarmDao.delete(alarm));
        databasePopulator.resetDatabase();
    }

    @Override
    public void setTemporaryDatabase(MockDatabase database) {
        mockDatabase = database;
    }
}
