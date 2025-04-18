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
package org.opennms.test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.opennms.core.test.ConfigurationTestUtils;
import org.opennms.core.utils.PropertiesUtils;
import org.springframework.beans.factory.InitializingBean;

/**
 * Support class to help with configuration that needs to happen in
 * integration tests before Spring attempts to do context initialization of
 * applicationContext-dao.xml.
 * In particular, this sets up system properties that are needed by Spring.
 * System properties are not set until afterPropertiesSet() is called.
 *
 * @author <a href="mailto:dj@opennms.org">DJ Gregor</a>
 * @version $Id: $
 */
public class DaoTestConfigBean implements InitializingBean {
    private String m_relativeHomeDirectory = null;
    private String m_rrdBinary = "/bin/true";
    private String m_relativeRrdBaseDirectory = "target/test/opennms-home/share/rrd";
    private static final String m_relativeImporterDirectory = "target/test/opennms-home/etc/imports";
    private static final String m_relativeForeignSourceDirectory = "target/test/opennms-home/etc/foreign-sources";

    /**
     * <p>Constructor for DaoTestConfigBean.</p>
     */
    public DaoTestConfigBean() {
    }

    /**
     * <p>afterPropertiesSet</p>
     */
    @Override
    public void afterPropertiesSet() {
        if (System.getProperty("org.opennms.netmgt.icmp.pingerClass") == null) {
            System.setProperty("org.opennms.netmgt.icmp.pingerClass", "org.opennms.netmgt.icmp.jna.JnaPinger");
        }
        if (System.getProperty("activemq.data") == null) {
            System.setProperty("activemq.data", "target/test/activemq");
        }
        if (System.getProperty("skipConfigUpgrades") == null) {
            System.setProperty("skipConfigUpgrades", "true");
        }

        // Load opennms.properties into the system properties
        Properties opennmsProperties = new Properties();
        try {
            opennmsProperties.load(ConfigurationTestUtils.getInputStreamForConfigFile("opennms.properties"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        // Do any necessary substitutions that are normally handled by maven
        Properties substitutions = new Properties();
        substitutions.setProperty("install.database.name", "opennms");
        substitutions.setProperty("install.database.driver", "org.postgres.Driver");
        substitutions.setProperty("install.share.dir", "target/test/share");
        substitutions.setProperty("install.logs.dir", "target/test/logs");
        for (Map.Entry<Object, Object> entry : opennmsProperties.entrySet()) {
            //System.err.println((String)entry.getKey() + " -> " + PropertiesUtils.substitute((String)entry.getValue(), substitutions));
            System.setProperty((String)entry.getKey(), PropertiesUtils.substitute((String)entry.getValue(), substitutions));
        }

        if (m_relativeHomeDirectory != null) {
            ConfigurationTestUtils.setRelativeHomeDirectory(m_relativeHomeDirectory);
        } else {
            ConfigurationTestUtils.setAbsoluteHomeDirectory(ConfigurationTestUtils.getDaemonEtcDirectory().getParentFile().getAbsolutePath());
        }

        ConfigurationTestUtils.setRrdBinary(m_rrdBinary);
        ConfigurationTestUtils.setRelativeRrdBaseDirectory(m_relativeRrdBaseDirectory);
        ConfigurationTestUtils.setRelativeImporterDirectory(m_relativeImporterDirectory);
        ConfigurationTestUtils.setRelativeForeignSourceDirectory(m_relativeForeignSourceDirectory);
    }

    /**
     * <p>getRelativeHomeDirectory</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getRelativeHomeDirectory() {
        return m_relativeHomeDirectory;
    }

    /**
     * <p>setRelativeHomeDirectory</p>
     *
     * @param relativeHomeDirectory a {@link java.lang.String} object.
     */
    public void setRelativeHomeDirectory(String relativeHomeDirectory) {
        m_relativeHomeDirectory = relativeHomeDirectory;
    }

    /**
     * <p>getRelativeRrdBaseDirectory</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getRelativeRrdBaseDirectory() {
        return m_relativeRrdBaseDirectory;
    }

    /**
     * <p>setRelativeRrdBaseDirectory</p>
     *
     * @param rrdBaseDirectory a {@link java.lang.String} object.
     */
    public void setRelativeRrdBaseDirectory(String rrdBaseDirectory) {
        m_relativeRrdBaseDirectory = rrdBaseDirectory;
    }

    /**
     * <p>getRrdBinary</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getRrdBinary() {
        return m_rrdBinary;
    }

    /**
     * <p>setRrdBinary</p>
     *
     * @param rrdBinary a {@link java.lang.String} object.
     */
    public void setRrdBinary(String rrdBinary) {
        m_rrdBinary = rrdBinary;
    }
}
;
