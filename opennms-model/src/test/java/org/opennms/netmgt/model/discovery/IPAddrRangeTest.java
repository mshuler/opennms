/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2010-2012 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.model.discovery;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

import junit.framework.TestCase;

/**
 * IPAddressRangeTest
 *
 * @author brozow
 */
public class IPAddrRangeTest extends TestCase {

    private final IPAddress addr2 = new IPAddress("192.168.1.3");
    private final IPAddress addr3 = new IPAddress("192.168.1.5");

    private final IPAddrRange singleton;
    private final IPAddrRange small;

    public IPAddrRangeTest() throws UnknownHostException {
        small = new IPAddrRange(addr2.toString(), addr3.toString());
        singleton = new IPAddrRange(addr2.toString(), addr2.toString());
    }

    public void testIterator() {
        // assertEquals(3, small.size());
        Iterator<InetAddress> it = small.iterator();
        assertTrue(it.hasNext());
        assertEquals(addr2.toInetAddress(), it.next());
        assertTrue(it.hasNext());
        assertEquals(addr2.incr().toInetAddress(), it.next());
        assertTrue(it.hasNext());
        assertEquals(addr3.toInetAddress(), it.next());
        assertFalse(it.hasNext());
    }

    public void testIterateSingleton() {
        Iterator<InetAddress> it = singleton.iterator();
        assertTrue(it.hasNext());
        assertEquals(addr2.toInetAddress(), it.next());
        assertFalse(it.hasNext());
    }

}
