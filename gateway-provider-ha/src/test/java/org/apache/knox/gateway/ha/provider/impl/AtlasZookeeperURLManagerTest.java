/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.ha.provider.impl;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingCluster;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.ha.provider.URLManager;
import org.apache.knox.gateway.ha.provider.URLManagerLoader;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class AtlasZookeeperURLManagerTest {

    private TestingCluster cluster;
    private AtlasZookeeperURLManager manager;
    private static String atlasNode1 = "http://atlas.node1:21000";
    private static String atlasNode2 = "http://atlas.node2:21000";

    @Before
    public void setup() throws Exception {
        cluster = new TestingCluster(3);
        cluster.start();

        CuratorFramework zooKeeperClient =
                CuratorFrameworkFactory.builder().connectString(cluster.getConnectString())
                                                 .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                                                 .build();

        zooKeeperClient.start();
        zooKeeperClient.create().forPath("/apache_atlas");
        zooKeeperClient.create().forPath("/apache_atlas/active_server_info");
        zooKeeperClient.setData().forPath("/apache_atlas/active_server_info",
                                          atlasNode1.getBytes(Charset.forName("UTF-8")));
        zooKeeperClient.close();
        setAtlasActiveHostURLInZookeeper(atlasNode1);

        manager = new AtlasZookeeperURLManager();
        HaServiceConfig config = new DefaultHaServiceConfig("ATLAS-API");
        config.setEnabled(true);
        config.setZookeeperEnsemble(cluster.getConnectString());
        config.setZookeeperNamespace("apache_atlas");
        manager.setConfig(config);
    }

    @After
    public void teardown() throws IOException {
        cluster.stop();
    }

    @Test
    public void testAtlasActiveUrlIsSetCorrectlyAfterLookUpFromZK() {
        manager.lookupURLs();
        List<String> urls = manager.getURLs();
        assertEquals(atlasNode1, urls.get(0));
    }

    @Test
    public void testMarkFailedCorrectlyResetTheEarlierUrl() throws Exception {
        setAtlasActiveHostURLInZookeeper(atlasNode2);

        manager.markFailed("http://atlas.node1:21000");
        List<String> urls = manager.getURLs();
        assertNotEquals(atlasNode1, urls.get(0));
        assertEquals(atlasNode2, urls.get(0));
    }

    @Test
    public void testAtlasURLManagerLoadingForAtlasApiService() {
        doTestAtlasZooKeeperURLManager("ATLAS-API", true, cluster.getConnectString(), "apache_atlas");
    }

    @Test
    public void testAtlasURLManagerLoadingForAtlasUIService() {
        doTestAtlasZooKeeperURLManager("ATLAS", true, cluster.getConnectString(), "apache_atlas");
    }

    @Test
    public void testAtlasURLManagerDefaultNamespace() {
        doTestAtlasZooKeeperURLManager("ATLAS", true, cluster.getConnectString(), null);
    }

    @Test
    public void testAtlasAPIURLManagerDefaultNamespace() {
        doTestAtlasZooKeeperURLManager("ATLAS-API", true, cluster.getConnectString(), null);
    }

    @Test
    public void testAtlasURLManagerWithLeadingSlashNamespace() {
        doTestAtlasZooKeeperURLManager("ATLAS", true, cluster.getConnectString(), "/apache_atlas");
    }

    @Test
    public void testAtlasAPIURLManagerWithLeadingSlashNamespace() {
        doTestAtlasZooKeeperURLManager("ATLAS-API", true, cluster.getConnectString(), "/apache_atlas");
    }

    private void doTestAtlasZooKeeperURLManager(final String  serviceName,
                                                final boolean enabled,
                                                final String  ensemble,
                                                final String  namespace) {
        HaServiceConfig config = new DefaultHaServiceConfig(serviceName);
        config.setEnabled(enabled);
        config.setZookeeperEnsemble(ensemble);
        config.setZookeeperNamespace(namespace);
        URLManager manager = URLManagerLoader.loadURLManager(config);
        Assert.assertNotNull(manager);
        Assert.assertTrue(manager instanceof AtlasZookeeperURLManager);
    }


    void setAtlasActiveHostURLInZookeeper(String activeURL) throws Exception {

        CuratorFramework zooKeeperClient =
                CuratorFrameworkFactory.builder().connectString(cluster.getConnectString())
                                                 .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                                                 .build();

        zooKeeperClient.start();
        zooKeeperClient.setData().forPath("/apache_atlas/active_server_info",
                                          activeURL.getBytes(Charset.forName("UTF-8")));
        zooKeeperClient.close();
    }

}
