package org.ecsoya.fabric.network;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Holds the details of a Certificate Authority
 */
public class FabricCAInfo {
	private final String name;
	private final String url;
	private final Properties httpOptions;
	private final String mspid;
	private String caName; // The "optional" caName specified in the config, as opposed to its "config"
							// name
	private Properties properties;

	private final List<FabricUserInfo> registrars;

	public FabricCAInfo(String name, String mspid, String url, List<FabricUserInfo> registrars,
			Properties httpOptions) {
		this.name = name;
		this.url = url;
		this.httpOptions = httpOptions;
		this.registrars = registrars;
		this.mspid = mspid;
	}

	public void setCaName(String caName) {
		this.caName = caName;
	}

	public String getName() {
		return name;
	}

	public String getCAName() {
		return caName;
	}

	public String getUrl() {
		return url;
	}

	public Properties getHttpOptions() {
		return httpOptions;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	public Properties getProperties() {
		return this.properties;
	}

	public Collection<FabricUserInfo> getRegistrars() {
		return new LinkedList<>(registrars);
	}

	public String getMspid() {
		return mspid;
	}

}