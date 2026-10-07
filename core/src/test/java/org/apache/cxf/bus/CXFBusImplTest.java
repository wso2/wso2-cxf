/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.bus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cxf.Bus;
import org.apache.cxf.Bus.BusState;
import org.apache.cxf.BusException;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.buslifecycle.BusLifeCycleListener;
import org.apache.cxf.buslifecycle.BusLifeCycleManager;
import org.apache.cxf.management.InstrumentationManager;
import org.apache.cxf.phase.PhaseManager;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.DestinationFactoryManager;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class CXFBusImplTest {

    @Test
    public void testThreadBus() throws BusException {
        BusFactory.setDefaultBus(null);
        BusFactory.setThreadDefaultBus(null);
        Bus bus = BusFactory.newInstance().createBus();
        Bus b2 = BusFactory.getThreadDefaultBus(false);
        assertSame(bus, b2);
        bus.shutdown(true);
    }
    @Test
    public void testConstructionWithoutExtensions() throws BusException {

        Bus bus = new ExtensionManagerBus();
        assertNotNull(bus.getExtension(BindingFactoryManager.class));
        assertNotNull(bus.getExtension(ConduitInitiatorManager.class));
        assertNotNull(bus.getExtension(DestinationFactoryManager.class));
        assertNotNull(bus.getExtension(PhaseManager.class));
        bus.shutdown(true);
    }

    @Test
    public void testConstructionWithExtensions() throws BusException {

        IMocksControl control;
        BindingFactoryManager bindingFactoryManager;
        InstrumentationManager instrumentationManager;
        PhaseManager phaseManager;

        control = EasyMock.createNiceControl();

        Map<Class<?>, Object> extensions = new HashMap<>();
        bindingFactoryManager = control.createMock(BindingFactoryManager.class);
        instrumentationManager = control.createMock(InstrumentationManager.class);
        phaseManager = control.createMock(PhaseManager.class);

        extensions.put(BindingFactoryManager.class, bindingFactoryManager);
        extensions.put(InstrumentationManager.class, instrumentationManager);
        extensions.put(PhaseManager.class, phaseManager);

        Bus bus = new ExtensionManagerBus(extensions);

        assertSame(bindingFactoryManager, bus.getExtension(BindingFactoryManager.class));
        assertSame(instrumentationManager, bus.getExtension(InstrumentationManager.class));
        assertSame(phaseManager, bus.getExtension(PhaseManager.class));

    }

    @Test
    public void testExtensions() {
        Bus bus = new ExtensionManagerBus();
        String extension = "CXF";
        bus.setExtension(extension, String.class);
        assertSame(extension, bus.getExtension(String.class));
        bus.shutdown(true);
    }

    @Test
    public void testBusID() {
        Bus bus = new ExtensionManagerBus();
        String id = bus.getId();
        assertEquals("The bus id should be cxf", id, Bus.DEFAULT_BUS_ID + Math.abs(bus.hashCode()));
        bus.setId("test");
        assertEquals("The bus id should be changed", "test", bus.getId());
        bus.shutdown(true);
    }

    @Test
    public void testShutdownWithBusLifecycle() {
        final Bus bus = new ExtensionManagerBus();
        BusLifeCycleManager lifeCycleManager = bus.getExtension(BusLifeCycleManager.class);
        BusLifeCycleListener listener = EasyMock.createMock(BusLifeCycleListener.class);
        EasyMock.reset(listener);
        listener.preShutdown();
        EasyMock.expectLastCall();
        listener.postShutdown();
        EasyMock.expectLastCall();
        EasyMock.replay(listener);
        lifeCycleManager.registerLifeCycleListener(listener);
        bus.shutdown(true);
        EasyMock.verify(listener);
        bus.shutdown(true);
    }

    @Test
    public void testShutdownRunsAllPhasesWhenPreShutdownFails() {
        final ExtensionManagerBus bus = new ExtensionManagerBus();
        BusFactory.setDefaultBus(bus);
        BusFactory.setThreadDefaultBus(bus);
        BusLifeCycleManager lifeCycleManager = bus.getExtension(BusLifeCycleManager.class);
        List<String> calls = new ArrayList<>();
        // listeners are notified in reverse registration order, so the failing one runs first
        lifeCycleManager.registerLifeCycleListener(new RecordingListener("cleaner", calls, null, null));
        RuntimeException preFailure = new IllegalStateException("pre");
        lifeCycleManager.registerLifeCycleListener(new RecordingListener("server", calls, preFailure, null));

        try {
            bus.shutdown(true);
            fail("Expected the preShutdown failure to be rethrown");
        } catch (IllegalStateException ex) {
            assertSame(preFailure, ex);
            assertEquals(0, ex.getSuppressed().length);
        }
        assertEquals(Arrays.asList("server.pre", "cleaner.pre", "server.post", "cleaner.post"), calls);
        assertEquals(BusState.SHUTDOWN, bus.getState());
        assertNull(BusFactory.getDefaultBus(false));
        assertNull(BusFactory.getThreadDefaultBus(false));
    }

    @Test
    public void testShutdownRethrowsFirstFailureWithLaterOnesSuppressed() {
        final ExtensionManagerBus bus = new ExtensionManagerBus();
        BusLifeCycleManager lifeCycleManager = bus.getExtension(BusLifeCycleManager.class);
        List<String> calls = new ArrayList<>();
        RuntimeException postFailure1 = new IllegalArgumentException("post1");
        lifeCycleManager.registerLifeCycleListener(new RecordingListener("first", calls, null, postFailure1));
        RuntimeException preFailure = new IllegalStateException("pre");
        RuntimeException postFailure2 = new IllegalArgumentException("post2");
        lifeCycleManager.registerLifeCycleListener(new RecordingListener("second", calls, preFailure, postFailure2));

        try {
            bus.shutdown(true);
            fail("Expected the preShutdown failure to be rethrown");
        } catch (IllegalStateException ex) {
            assertSame(preFailure, ex);
            // the lifecycle manager reports its own first failure, with later listener failures suppressed on it
            assertArrayEquals(new Throwable[] {postFailure2}, ex.getSuppressed());
            assertArrayEquals(new Throwable[] {postFailure1}, postFailure2.getSuppressed());
        }
        assertEquals(Arrays.asList("second.pre", "first.pre", "second.post", "first.post"), calls);
        assertEquals(BusState.SHUTDOWN, bus.getState());
    }

    @Test
    public void testShutdownRunsAllPostShutdownListenersWhenOneFails() {
        final ExtensionManagerBus bus = new ExtensionManagerBus();
        BusFactory.setDefaultBus(bus);
        BusLifeCycleManager lifeCycleManager = bus.getExtension(BusLifeCycleManager.class);
        List<String> calls = new ArrayList<>();
        lifeCycleManager.registerLifeCycleListener(new RecordingListener("cleaner", calls, null, null));
        RuntimeException postFailure = new IllegalStateException("post");
        lifeCycleManager.registerLifeCycleListener(new RecordingListener("server", calls, null, postFailure));

        try {
            bus.shutdown(true);
            fail("Expected the postShutdown failure to be rethrown");
        } catch (IllegalStateException ex) {
            assertSame(postFailure, ex);
        }
        assertEquals(Arrays.asList("server.pre", "cleaner.pre", "server.post", "cleaner.post"), calls);
        assertEquals(BusState.SHUTDOWN, bus.getState());
        assertNull(BusFactory.getDefaultBus(false));
    }

    @Test
    public void testShutdownWithoutFailuresIsUnchanged() {
        final ExtensionManagerBus bus = new ExtensionManagerBus();
        BusLifeCycleManager lifeCycleManager = bus.getExtension(BusLifeCycleManager.class);
        List<String> calls = new ArrayList<>();
        lifeCycleManager.registerLifeCycleListener(new RecordingListener("a", calls, null, null));
        lifeCycleManager.registerLifeCycleListener(new RecordingListener("b", calls, null, null));
        bus.shutdown(true);
        assertEquals(Arrays.asList("b.pre", "a.pre", "b.post", "a.post"), calls);
        assertEquals(BusState.SHUTDOWN, bus.getState());
    }

    private static final class RecordingListener implements BusLifeCycleListener {
        private final String name;
        private final List<String> calls;
        private final RuntimeException preFailure;
        private final RuntimeException postFailure;

        RecordingListener(String name, List<String> calls, RuntimeException preFailure,
                          RuntimeException postFailure) {
            this.name = name;
            this.calls = calls;
            this.preFailure = preFailure;
            this.postFailure = postFailure;
        }

        public void initComplete() {
        }

        public void preShutdown() {
            calls.add(name + ".pre");
            if (preFailure != null) {
                throw preFailure;
            }
        }

        public void postShutdown() {
            calls.add(name + ".post");
            if (postFailure != null) {
                throw postFailure;
            }
        }
    }

}
