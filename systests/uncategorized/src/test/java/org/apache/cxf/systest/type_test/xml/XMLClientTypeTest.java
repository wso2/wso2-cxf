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
package org.apache.cxf.systest.type_test.xml;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.cxf.systest.type_test.AbstractTypeTestClient5;

import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;

import static org.junit.Assert.assertTrue;

public class XMLClientTypeTest extends AbstractTypeTestClient5 {
    static final String WSDL_PATH = "/wsdl/type_test/type_test_xml.wsdl";
    static final QName SERVICE_NAME = new QName("http://apache.org/type_test/xml", "XMLService");
    static final QName PORT_NAME = new QName("http://apache.org/type_test/xml", "XMLPort");
    static final String PORT = XMLServerImpl.PORT;
    // Not run on JDK 8.
    private static final Set<String> JDK8_SKIPPED = new HashSet<>(Arrays.asList(
            "testAnyURIRestriction",
            "testBase64Binary",
            "testComplexRestriction",
            "testComplexRestriction2",
            "testComplexRestriction3",
            "testComplexRestriction4",
            "testComplexRestriction5",
            "testHexBinaryRestriction",
            "testSimpleListRestriction2",
            "testSimpleRestriction",
            "testSimpleRestriction2",
            "testSimpleRestriction3",
            "testSimpleRestriction4",
            "testSimpleRestriction5",
            "testSimpleRestriction6"));

    @Rule
    public TestName testName = new TestName();

    @Before
    public void skipOnJdk8() {
        Assume.assumeFalse("1.8".equals(System.getProperty("java.specification.version"))
            && JDK8_SKIPPED.contains(testName.getMethodName()));
    }

    @Before
    public void updatePort() throws Exception {
        updateAddressPort(xmlClient, PORT);
    }

    @BeforeClass
    public static void startServers() throws Exception {
        boolean ok = launchServer(XMLServerImpl.class, true);
        assertTrue("failed to launch server", ok);
        initClient(SERVICE_NAME, PORT_NAME, WSDL_PATH);
    }
}
