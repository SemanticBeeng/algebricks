/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.algebricks.tests.util;

import java.util.EnumSet;
import java.util.UUID;

import edu.uci.ics.algebricks.config.AlgebricksConfig;
import edu.uci.ics.hyracks.api.client.HyracksLocalConnection;
import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.api.control.CCConfig;
import edu.uci.ics.hyracks.api.control.NCConfig;
import edu.uci.ics.hyracks.api.job.JobFlag;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.control.cc.ClusterControllerService;
import edu.uci.ics.hyracks.control.nc.NodeControllerService;

public class AlgebricksHyracksIntegrationUtil {

    public static final String NC1_ID = "nc1";
    public static final String NC2_ID = "nc2";

    public static final int DEFAULT_HYRACKS_CC_PORT = 1099;

    public static final int TEST_HYRACKS_CC_PORT = 4322;

    private static ClusterControllerService cc;
    private static NodeControllerService nc1;
    private static NodeControllerService nc2;
    private static IHyracksClientConnection hcc;

    public static void init() throws Exception {
        CCConfig ccConfig = new CCConfig();
        ccConfig.port = TEST_HYRACKS_CC_PORT;
        // ccConfig.useJOL = true;
        cc = new ClusterControllerService(ccConfig);
        cc.start();

        NCConfig ncConfig1 = new NCConfig();
        ncConfig1.ccHost = "localhost";
        ncConfig1.ccPort = TEST_HYRACKS_CC_PORT;
        ncConfig1.dataIPAddress = "127.0.0.1";
        ncConfig1.nodeId = NC1_ID;
        nc1 = new NodeControllerService(ncConfig1);
        nc1.start();

        NCConfig ncConfig2 = new NCConfig();
        ncConfig2.ccHost = "localhost";
        ncConfig2.ccPort = TEST_HYRACKS_CC_PORT;
        ncConfig2.dataIPAddress = "127.0.0.1";
        ncConfig2.nodeId = NC2_ID;
        nc2 = new NodeControllerService(ncConfig2);
        nc2.start();

        hcc = new HyracksLocalConnection(cc);
        hcc.createApplication(AlgebricksConfig.HYRACKS_APP_NAME, null);
    }

    public static void deinit() throws Exception {
        nc2.stop();
        nc1.stop();
        cc.stop();
    }

    public static void runJob(JobSpecification spec) throws Exception {
        UUID jobId = hcc.createJob(AlgebricksConfig.HYRACKS_APP_NAME, spec, EnumSet.of(JobFlag.PROFILE_RUNTIME));
        AlgebricksConfig.ALGEBRICKS_LOGGER.info(spec.toJSON().toString());
        cc.start(jobId);
        AlgebricksConfig.ALGEBRICKS_LOGGER.info(jobId.toString());
        cc.waitForCompletion(jobId);
    }

}
