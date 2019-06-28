package org.ecsoya.fabric.network;

import java.util.Properties;

// Holds a network "node" (eg. Peer, Orderer, EventHub)
public class FabricNetworkNode {

	private final String name;
	private final String url;
	private final String grpcUrl;
	private Properties properties;

	public FabricNetworkNode(String name, String url, String grpcUrl, Properties properties) {
		this.url = url;
		this.name = name;
		this.grpcUrl = grpcUrl;
		this.setProperties(properties);
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		if (grpcUrl != null) {
			if (!grpcUrl.startsWith("grpcs://")) {
				return "grpcs://" + grpcUrl;
			}
			return grpcUrl;
		}
		return url;
	}

	public Properties getProperties() {
		return properties;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

}