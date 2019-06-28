package org.ecsoya.fabric.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds details of an Organization
 */
public class FabricOrgInfo {

	private final String name;
	private final String mspId;
	private final List<String> peerNames = new ArrayList<>();
	private final List<FabricCAInfo> certificateAuthorities = new ArrayList<>();

	private String cryptoPath;

	FabricUserInfo peerAdmin;

	public FabricOrgInfo(String orgName, String mspId) {
		this.name = orgName;
		this.mspId = mspId;
	}

	public void addPeerName(String peerName) {
		peerNames.add(peerName);
	}

	public void addCertificateAuthority(FabricCAInfo ca) {
		certificateAuthorities.add(ca);
	}

	public String getName() {
		return name;
	}

	public String getMspId() {
		return mspId;
	}

	public List<String> getPeerNames() {
		return peerNames;
	}

	public List<FabricCAInfo> getCertificateAuthorities() {
		return certificateAuthorities;
	}

	/**
	 * Returns the associated admin user
	 *
	 * @return The admin user details
	 */
	public FabricUserInfo getPeerAdmin() {

		return peerAdmin;
	}

	public String getCryptoPath() {
		return cryptoPath;
	}

	public void setCryptoPath(String cryptoPath) {
		this.cryptoPath = cryptoPath;
	}

}