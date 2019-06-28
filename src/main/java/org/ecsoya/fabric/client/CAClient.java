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
package org.ecsoya.fabric.client;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ecsoya.fabric.network.FabricCAInfo;
import org.ecsoya.fabric.user.UserContext;
import org.ecsoya.fabric.util.FabricUtil;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException;

/**
 * Wrapper class for HFCAClient.
 * 
 * @author Balaji Kadambi
 *
 */

public class CAClient {

	private final HFCAClient instance;
	private final String caName;
	private final String caUrl;
	private UserContext adminContext;

	public CAClient(FabricCAInfo caInfo)
			throws MalformedURLException, InvalidArgumentException, IllegalAccessException, InstantiationException,
			ClassNotFoundException, CryptoException, org.hyperledger.fabric.sdk.exception.InvalidArgumentException,
			NoSuchMethodException, InvocationTargetException {
		if (caInfo == null) {
			throw new RuntimeException("CA info can't be empty.");
		}
		this.caName = caInfo.getName();
		this.caUrl = caInfo.getUrl();
		Properties properties = caInfo.getProperties();
		if (caName == null || caName.equals("") || caUrl == null || caUrl.equals("")) {
			throw new RuntimeException("Both the name and url of CA should be provided.");
		}

		this.instance = HFCAClient.createNewInstance(caName, caUrl, properties);
		CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
		this.instance.setCryptoSuite(cryptoSuite);
	}

	public HFCAClient getInstance() {
		return instance;
	}

	public UserContext getAdminUserContext() {
		return adminContext;
	}

	/**
	 * Set the admin user context for registering and enrolling users.
	 * 
	 * @param userContext
	 */
	public void setAdminUserContext(UserContext userContext) {
		this.adminContext = userContext;
	}

	/**
	 * Enroll admin user.
	 * 
	 * @param username
	 * @param password
	 * @return
	 * @throws Exception
	 */
	public UserContext enrollAdminUser(String username, String password) throws Exception {
		UserContext userContext = FabricUtil.readUserContext(adminContext.getAffiliation(), username);
		if (userContext != null) {
			Logger.getLogger(CAClient.class.getName()).log(Level.WARNING,
					"CA -" + caUrl + " admin is already enrolled.");
			return userContext;
		}
		Enrollment adminEnrollment = instance.enroll(username, password);
		adminContext.setEnrollment(adminEnrollment);
		Logger.getLogger(CAClient.class.getName()).log(Level.INFO, "CA -" + caUrl + " Enrolled Admin.");
		FabricUtil.writeUserContext(adminContext);
		return adminContext;
	}

	/**
	 * Register user.
	 * 
	 * @param username
	 * @param organization
	 * @return
	 * @throws Exception
	 */
	public String registerUser(String username, String organization) throws Exception {
		UserContext userContext = FabricUtil.readUserContext(adminContext.getAffiliation(), username);
		if (userContext != null) {
			Logger.getLogger(CAClient.class.getName()).log(Level.WARNING,
					"CA -" + caUrl + " User " + username + " is already registered.");
			return null;
		}
		RegistrationRequest rr = new RegistrationRequest(username, organization);
		String enrollmentSecret = instance.register(rr, adminContext);
		Logger.getLogger(CAClient.class.getName()).log(Level.INFO, "CA -" + caUrl + " Registered User - " + username);
		return enrollmentSecret;
	}

	/**
	 * Enroll user.
	 * 
	 * @param user
	 * @param secret
	 * @return
	 * @throws Exception
	 */
	public UserContext enrollUser(UserContext user, String secret) throws Exception {
		UserContext userContext = FabricUtil.readUserContext(adminContext.getAffiliation(), user.getName());
		if (userContext != null) {
			Logger.getLogger(CAClient.class.getName()).log(Level.WARNING,
					"CA -" + caUrl + " User " + user.getName() + " is already enrolled");
			return userContext;
		}
		Enrollment enrollment = instance.enroll(user.getName(), secret);
		user.setEnrollment(enrollment);
		FabricUtil.writeUserContext(user);
		Logger.getLogger(CAClient.class.getName()).log(Level.INFO,
				"CA -" + caUrl + " Enrolled User - " + user.getName());
		return user;
	}

}
