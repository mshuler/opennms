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
package org.opennms.core.utils;

import java.util.Properties;

import junit.framework.TestCase;

public class PropertiesUtilsTest extends TestCase {

    private Properties m_propsOne;
    private Properties m_propsTwo;

    @Override
    protected void setUp() throws Exception {
        m_propsOne = new Properties();
        m_propsOne.setProperty("prop.one", "one");
        m_propsOne.setProperty("prop.two", "two");
        m_propsOne.setProperty("prop.three", "3");
        m_propsOne.setProperty("prop.four", "${prop.three}+1");
        m_propsOne.setProperty("prop.five", "${prop.three}+${prop.two}");
        m_propsOne.setProperty("prop.six", "${prop.five}+${prop.one}");
        m_propsOne.setProperty("prop.infinite1", "${prop.infinite1}");
        m_propsOne.setProperty("prop.infinite2", "calling ${prop.infinite5}");
        m_propsOne.setProperty("prop.infinite3", "call ${prop.infinite2} again");
        m_propsOne.setProperty("prop.infinite4", "x${prop.three}+${prop.infinite3}x");
        m_propsOne.setProperty("prop.infinite5", "call ${prop.infinite4} ");
        m_propsOne.setProperty("prop.foo", "eff oh oh");
        
        m_propsTwo = new Properties();
        m_propsTwo.setProperty("prop.foo", "first geek ordinal");
    }

    @Override
    protected void tearDown() throws Exception {
    }
    
    public void testNull() {
        assertNull(PropertiesUtils.substitute(null, m_propsOne));
    }
    
    public void testNoSubstitution() {
        assertEquals("nosubst", PropertiesUtils.substitute("nosubst", m_propsOne));
        assertEquals("no${subst", PropertiesUtils.substitute("no${subst", m_propsOne));
        assertEquals("no}subst", PropertiesUtils.substitute("no}subst", m_propsOne));
        assertEquals("no${no.such.property}subst", PropertiesUtils.substitute("no${no.such.property}subst", m_propsOne));
        assertEquals("no\nsubst", PropertiesUtils.substitute("no" + (char)0x0A + "subst", m_propsOne));
    }
    
    public void testSingleSubstitution() {
        assertEquals("xonex", PropertiesUtils.substitute("x${prop.one}x", m_propsOne));
        assertEquals("onebegin", PropertiesUtils.substitute("${prop.one}begin", m_propsOne));
        assertEquals("endone", PropertiesUtils.substitute("end${prop.one}", m_propsOne));
        assertEquals("one\nsubst", PropertiesUtils.substitute("${prop.one}" + (char)0x0A + "subst", m_propsOne));
        assertEquals("subst\none", PropertiesUtils.substitute("subst" + (char)0x0A + "${prop.one}", m_propsOne));
    }
    
    public void testMultiSubstition() {
        assertEquals("xoneytwoz", PropertiesUtils.substitute("x${prop.one}y${prop.two}z", m_propsOne));
        assertEquals("wonextwoy3z", PropertiesUtils.substitute("w${prop.one}x${prop.two}y${prop.three}z", m_propsOne));
        assertEquals("onetwo3", PropertiesUtils.substitute("${prop.one}${prop.two}${prop.three}", m_propsOne));
        assertEquals("one\ntwo", PropertiesUtils.substitute("${prop.one}" + (char)0x0A + "${prop.two}", m_propsOne));
        assertEquals("two\none", PropertiesUtils.substitute("${prop.two}" + (char)0x0A + "${prop.one}", m_propsOne));
    }
    
    public void testRecursiveSubstitution() {
        assertEquals("a3+1b", PropertiesUtils.substitute("a${prop.four}b", m_propsOne));
        assertEquals("a3+twob", PropertiesUtils.substitute("a${prop.five}b", m_propsOne));
        assertEquals("a3+two+oneb", PropertiesUtils.substitute("a${prop.six}b", m_propsOne));
    }
    
    public void testSimpleInfiniteRecursion() {
        try {
            String val = PropertiesUtils.substitute("a${prop.infinite1}b", m_propsOne);
            fail("Unexpected value "+val);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().indexOf("prop.infinite1") >= 0);
        }
    }

    public void testComplexInfiniteRecursion() {
        try {
            String val = PropertiesUtils.substitute("a${prop.infinite5}b", m_propsOne);
            fail("Unexpected value "+val);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().indexOf("prop.infinite5") >= 0);
        }
    }
    
    public void testMultiPropsSubstitution() {
        assertEquals("eff oh oh", PropertiesUtils.substitute("${prop.foo}", m_propsOne, m_propsTwo));
        assertEquals("first geek ordinal", PropertiesUtils.substitute("${prop.foo}", m_propsTwo, m_propsOne));
    }
}
