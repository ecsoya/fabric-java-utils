/****************************************************** 
 *  Copyright 2018 IBM Corporation 
 *  Licensed under the Apache License, Version 2.0 (the "License"); 
 *  you may not use this file except in compliance with the License. 
 *  You may obtain a copy of the License at 
 *  http://www.apache.org/licenses/LICENSE-2.0 
 *  Unless required by applicable law or agreed to in writing, software 
 *  distributed under the License is distributed on an "AS IS" BASIS, 
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 *  See the License for the specific language governing permissions and 
 *  limitations under the License.
 */
package org.ecsoya.fabric.tests;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;

import org.ecsoya.fabric.client.CAClient;
import org.ecsoya.fabric.network.FabricCAInfo;
import org.ecsoya.fabric.user.UserContext;
import org.ecsoya.fabric.util.FabricUtil;

/**
 * 
 * @author Balaji Kadambi
 *
 */

public class RegisterEnrollUser {

	public static void main(String args[]) {
		try {
			FabricUtil.cleanUp();

			File caPem = new File("src/main/resources/networkspec/fabric/keyfiles/ecsoya/tlsca/tlsca.ecsoya-cert.pem");

			byte[] pemBytes = Files.readAllBytes(caPem.toPath());

			Properties properties = new Properties();
			properties.put("pemBytes", pemBytes);
			properties.setProperty("allowAllHostNames", "true");

			FabricCAInfo caInfo = new FabricCAInfo("ca1-ecsoya", "", "https://132.232.70.192:31568", null, null);
			caInfo.setProperties(properties);

			CAClient caClient = new CAClient(caInfo);
			// Enroll Admin to Org1MSP
			UserContext adminUserContext = new UserContext();
			adminUserContext.setName("admin");
			adminUserContext.setAffiliation("ecsoya");
			adminUserContext.setMspId("ecsoya");
			caClient.setAdminUserContext(adminUserContext);
			adminUserContext = caClient.enrollAdminUser("admin", "adminpw");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
