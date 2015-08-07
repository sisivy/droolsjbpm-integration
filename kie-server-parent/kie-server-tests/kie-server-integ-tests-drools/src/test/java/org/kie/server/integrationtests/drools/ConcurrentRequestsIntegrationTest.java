/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.kie.server.integrationtests.drools;

import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.ReleaseId;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.RuleServicesClient;
import org.kie.server.integrationtests.shared.RestJmsXstreamSharedBaseIntegrationTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

public class ConcurrentRequestsIntegrationTest extends RestJmsXstreamSharedBaseIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentRequestsIntegrationTest.class);

    private static ReleaseId releaseId1 = new ReleaseId("org.kie.server.testing", "stateless-session-kjar", "1.0.0-SNAPSHOT");

    private static final String CONTAINER_ID = "kie-concurrent";
    private static final int NR_OF_THREADS = 5;
    private static final int NR_OF_REQUESTS_PER_THREAD = 20;

    @BeforeClass
    public static void initialize() throws Exception {
        buildAndDeployCommonMavenParent();
        buildAndDeployMavenProject(ClassLoader.class.getResource("/kjars-sources/stateless-session-kjar").getFile());
    }

    @Test
    public void testCallingStatelessSessionFromMultipleThreads() throws Exception {
        client.createContainer(CONTAINER_ID, new KieContainerResource(CONTAINER_ID, releaseId1));
        List<Future<String>> futureResults = new ArrayList<Future<String>>();
        ExecutorService es = Executors.newFixedThreadPool(NR_OF_THREADS);
        for (int i = 0; i < NR_OF_THREADS; i++) {
            futureResults.add(es.submit(new Worker(createDefaultClient())));
        }
        es.shutdown();
        for (Future<String> future : futureResults) {
            assertEquals("SUCCESS", future.get());
        }
    }

    public static class Worker implements Callable<String> {
        private static Logger logger = LoggerFactory.getLogger(Worker.class);
        private final KieServicesClient client;
        private final RuleServicesClient ruleServicesClient;

        public Worker(KieServicesClient client) {
            this.client = client;
            this.ruleServicesClient = client.getServicesClient(RuleServicesClient.class);
        }

        @Override
        public String call() {
            String payload = "<batch-execution lookup=\"kbase1.stateless\">\n" +
                    "  <insert out-identifier=\"person1\">\n" +
                    "    <org.kie.server.testing.Person>\n" +
                    "      <firstname>Darth</firstname>\n" +
                    "    </org.kie.server.testing.Person>\n" +
                    "  </insert>\n" +
                    "</batch-execution>";
            long threadId = Thread.currentThread().getId();
            ServiceResponse<String> reply;
            for (int i = 0; i < NR_OF_REQUESTS_PER_THREAD; i++) {
                logger.trace("Container call #{}, thread-id={}", i, threadId);
                reply = ruleServicesClient.executeCommands(CONTAINER_ID, payload);
                logger.trace("Container reply for request #{}: {}, thread-id={}", i, reply, threadId);
                assertEquals(ServiceResponse.ResponseType.SUCCESS, reply.getType());
            }
            return "SUCCESS";
        }
    }

}