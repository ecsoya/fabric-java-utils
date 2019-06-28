package org.ecsoya.fabric.network;

import org.ecsoya.fabric.user.UserContext;

/**
 * Holds details of a User
 */
public class FabricUserInfo extends UserContext {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	protected String enrollSecret;

	public FabricUserInfo(String mspid, String name, String enrollSecret) {
		this.enrollSecret = enrollSecret;
		setName(name);
		setMspId(mspid);
	}

	public String getEnrollSecret() {
		return enrollSecret;
	}

	public void setEnrollSecret(String enrollSecret) {
		this.enrollSecret = enrollSecret;
	}

}