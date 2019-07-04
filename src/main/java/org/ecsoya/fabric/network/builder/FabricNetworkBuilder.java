package org.ecsoya.fabric.network.builder;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class FabricNetworkBuilder {

	private String name;

	private String clientOrg;

	private String ordererOrg;

	private String[] orderers;

	private String[] peerOrgs;

	private String[] peers;

	private String[] channels;

	private Map<String, Object> urls = new HashMap<>();
	private Map<String, Object> ports = new HashMap<>();
	private File root;

	public FabricNetworkBuilder root(File root) {
		this.root = root;
		return this;
	}

	public FabricNetworkBuilder name(String name) {
		this.name = name;
		return this;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public FabricNetworkBuilder url(String org, String peer, String url) {
		Object value = urls.get(org);
		if (peer != null) {
			if (!(value instanceof Map)) {
				Map map = new HashMap<>();
				map.put(peer, url);
				urls.put(org, map);
			} else {
				((Map) value).put(peer, url);
			}

		} else {
			urls.put(org, url);
		}
		return this;
	}

	public FabricNetworkBuilder port(String org, String peer, int port) {
		Object value = ports.get(org);
		if (peer != null) {
			if (!(value instanceof Map)) {
				Map map = new HashMap<>();
				map.put(peer, port);
				urls.put(org, map);
			} else {
				((Map) value).put(peer, port);
			}

		} else {
			ports.put(org, port);
		}
		return this;
	}

	public FabricNetworkBuilder clientOrg(String clientOrg) {
		this.clientOrg = clientOrg;
		return this;
	}

	public FabricNetworkBuilder ordererOrg(String ordererOrg) {
		this.ordererOrg = ordererOrg;
		return this;
	}

	public FabricNetworkBuilder peerOrgs(String... peerOrgs) {
		this.peerOrgs = peerOrgs;
		return this;
	}

	public FabricNetworkBuilder orderers(String... orderers) {
		this.orderers = orderers;
		return this;
	}

	public FabricNetworkBuilder peers(String... peers) {
		this.peers = peers;
		return this;
	}

	public FabricNetworkBuilder channels(String... channels) {
		this.channels = channels;
		return this;
	}

	public JsonObject build() throws NetworkBuilderException {
		if (name == null) {
			throw new NetworkBuilderException("The network name is not specified.");
		}
		if (ordererOrg == null) {
			throw new NetworkBuilderException("The network ordererOrg is not specified.");
		}
		if (clientOrg == null) {
			throw new NetworkBuilderException("The client organization is not specified.");
		}

		if (channels == null || channels.length == 0) {
			throw new NetworkBuilderException("The network channels is not specified.");
		}

		if (peerOrgs == null || peerOrgs.length == 0) {
			throw new NetworkBuilderException("The network peerOrgs is not specified.");
		}

		if (peers == null || peers.length == 0) {
			throw new NetworkBuilderException("The network peers is not specified.");
		}
		if (orderers == null || orderers.length == 0) {
			throw new NetworkBuilderException("The network orderers is not specified.");
		}

		if (root == null || !root.exists()) {
			throw new NetworkBuilderException("The network root directory is not existed.");
		}
		JsonObject root = new JsonObject();

		root.addProperty("name", name);
		root.addProperty("version", "1.0.0");
		root.addProperty("x-type", "hlfv1");

		// client
		JsonObject client = buildClient();
		root.add("client", client);

		// channels
		JsonObject channels = buildChannels();
		root.add("channels", channels);

		// organizations
		JsonObject organizations = buildOrganizations();
		root.add("organizations", organizations);

		// orderers
		JsonObject orderers = buildOrderers();
		root.add("orderers", orderers);

		// peers
		JsonObject peers = buildPeers();
		root.add("peers", peers);

		// certificateAuthorities
		JsonObject certificateAuthorities = buildCertificateAuthorities();
		root.add("certificateAuthorities", certificateAuthorities);
		return root;
	}

	private JsonObject buildCertificateAuthorities() {
		JsonObject root = new JsonObject();

		JsonObject caroot = buildCertificateAuthorityNode(ordererOrg);
		root.add("ca." + ordererOrg, caroot);

		for (String org : peerOrgs) {
			JsonObject node = buildCertificateAuthorityNode(org);
			root.add("ca." + org, node);
		}

		return root;
	}

	private JsonObject buildCertificateAuthorityNode(String org) {
		JsonObject node = new JsonObject();

//		node.addProperty("url", "https://" + org + "-rootca." + url + ":7054");

		JsonObject httpOptions = new JsonObject();
		httpOptions.addProperty("verify", false);
		node.add("httpOptions", httpOptions);

		JsonObject tlsCACerts = new JsonObject();
		tlsCACerts.addProperty("path", getCertPath(org));
		node.add("tlsCACerts", tlsCACerts);

		return node;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private String getUrl(String org, String peer) throws NetworkBuilderException {
		Object value = urls.get(org);
		if (peer == null) {
			if (value instanceof String) {
				return (String) value;
			}
			throw new NetworkBuilderException("Unnable to find URL for org: " + org);
		} else if (value instanceof Map) {

			Map<String, String> map = (Map) value;
			if (map == null || map.isEmpty() || !map.containsKey(peer)) {
				throw new NetworkBuilderException("Unnable to find URL for peer: " + peer + " in org: " + org);
			}
			return map.get(peer);
		}
		throw new NetworkBuilderException("Unnable to find URL for peer: " + peer + " in org: " + org);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Integer getPort(String org, String peer, int defaultValue) {
		Object value = ports.get(org);
		if (peer == null) {
			if (value instanceof Integer) {
				return (Integer) value;
			}
			return defaultValue;
		} else if (value instanceof Map) {

			Map<String, Integer> map = (Map) value;
			if (map == null || map.isEmpty() || !map.containsKey(peer)) {
				return defaultValue;
			}
			return map.get(peer);
		}
		return defaultValue;
	}

	private JsonObject buildPeers() throws NetworkBuilderException {
		JsonObject root = new JsonObject();
		for (String p : peers) {
			for (String o : peerOrgs) {
				String name = p + "." + o;
				JsonObject node = new JsonObject();
				node.addProperty("url", "grpcs://" + getUrl(o, p) + ":" + getPort(o, p, 7051));
				node.addProperty("eventUrl", "grpcs://" + getUrl(o, p) + ":7053");
				JsonObject grpcOptions = new JsonObject();
				grpcOptions.addProperty("ssl-target-name-override", name);
				grpcOptions.addProperty("allow-insecure", 0);
				grpcOptions.addProperty("trustServerCertificate", true);

				node.add("grpcOptions", grpcOptions);

				JsonObject tlsCACerts = new JsonObject();
				tlsCACerts.addProperty("path", getCertPath(o));
				node.add("tlsCACerts", tlsCACerts);
				root.add(name, node);
			}
		}
		return root;
	}

	private JsonObject buildOrderers() throws NetworkBuilderException {
		JsonObject node = new JsonObject();
		for (String org : orderers) {
			JsonObject orgNode = new JsonObject();
			orgNode.addProperty("url", "grpcs://" + getUrl(org, null) + ":7050");

			JsonObject grpcOptions = new JsonObject();
			grpcOptions.addProperty("ssl-target-name-override", org);
			grpcOptions.addProperty("allow-insecure", 0);
			orgNode.add("grpcOptions", grpcOptions);

			JsonObject tlsCACerts = new JsonObject();
			tlsCACerts.addProperty("path", getCertPath(ordererOrg));
			orgNode.add("tlsCACerts", tlsCACerts);
			node.add(org, orgNode);
		}
		return node;
	}

	private JsonObject buildOrganizations() {
		JsonObject node = new JsonObject();
		JsonObject ordererOrgNode = buildOrgNode(ordererOrg, 0);
		node.add(ordererOrg, ordererOrgNode);

		for (String org : peerOrgs) {
			JsonObject child = buildOrgNode(org, 2);
			node.add(org, child);
		}
		return node;
	}

	private JsonObject buildOrgNode(String org, int numOfPeers) {
		JsonObject ordererOrgNode = new JsonObject();
		ordererOrgNode.addProperty("mspid", org.replaceAll(".ptgblock.cn", "") + "MSP");

		JsonArray certificateAuthorities = new JsonArray();
		certificateAuthorities.add("ca." + org);
		ordererOrgNode.add("certificateAuthorities", certificateAuthorities);

		JsonObject adminPrivateKey = new JsonObject();
		adminPrivateKey.addProperty("path", getPrivateKeyPath(org));
		ordererOrgNode.add("adminPrivateKey", adminPrivateKey);

		JsonObject signedCert = new JsonObject();
		signedCert.addProperty("path", getCertPath(org));
		ordererOrgNode.add("signedCert", signedCert);
		if (numOfPeers > 0) {
			JsonArray peers = new JsonArray();
			for (int i = 0; i < numOfPeers; i++) {
				peers.add("peer" + i + "." + org);
			}
			ordererOrgNode.add("peers", peers);
		}
		return ordererOrgNode;
	}

	private String getCertPath(String org) {
		File file = new File(root, org + "/users/Admin@" + org + "/msp/admincerts/Admin@" + org + "-cert.pem");
		if (!file.exists()) {
			return null;
		}

		URI path = file.toURI();
		String absolutePath = root.getAbsolutePath();

		int index = absolutePath.indexOf("/src");
		if (index != -1) {
			String srcParent = absolutePath.substring(0, index);
			path = new File(srcParent).toURI().relativize(path);
		} else {
			path = root.toURI().relativize(path);
		}

		return path.getPath();
	}

	private String getPrivateKeyPath(String org) {
		File dir = new File(root, org + "/users/Admin@" + org + "/msp/keystore");
		if (!dir.exists()) {
			return null;
		}
		File[] listFiles = dir.listFiles();
		if (listFiles.length == 0) {
			return null;
		}
		File keyFile = listFiles[0];

		URI path = keyFile.toURI();
		String absolutePath = root.getAbsolutePath();

		int index = absolutePath.indexOf("/src");
		if (index != -1) {
			String srcParent = absolutePath.substring(0, index);
			path = new File(srcParent).toURI().relativize(path);
		} else {
			path = root.toURI().relativize(path);
		}

		return path.getPath();
	}

	private JsonObject buildChannels() {
		JsonObject channelsNode = new JsonObject();

		for (String channel : channels) {
			JsonObject node = new JsonObject();
			// orderers
			JsonArray orderers = new JsonArray();
			for (String org : this.orderers) {
				orderers.add(org);
			}
			node.add("orderers", orderers);

			// peers
			JsonObject peersNode = new JsonObject();
			for (String peer : peers) {
				for (String org : peerOrgs) {
					JsonObject o = new JsonObject();
					o.addProperty("endorsingPeer", true);
					o.addProperty("chaincodeQuery", true);
					o.addProperty("eventSource", true);
					peersNode.add(peer + "." + org, o);
				}

			}
			node.add("peers", peersNode);

			// policies
			JsonObject policies = new JsonObject();
			JsonObject queryChannelConfig = new JsonObject();
			queryChannelConfig.addProperty("minResponses", 1);
			queryChannelConfig.addProperty("maxTargets", 1);

			JsonObject retryOpts = new JsonObject();
			retryOpts.addProperty("attempts", 5);
			retryOpts.addProperty("initialBackoff", "500ms");
			retryOpts.addProperty("maxBackoff", "5s");
			retryOpts.addProperty("backoffFactor", "2.0");
			queryChannelConfig.add("retryOpts", retryOpts);
			node.add("policies", policies);
			channelsNode.add(channel, node);
		}

		return channelsNode;
	}

	private JsonObject buildClient() {
		JsonObject client = new JsonObject();

		JsonObject logging = new JsonObject();
		logging.addProperty("level", "debug");
		client.add("logging", logging);

		JsonObject connection = new JsonObject();
		JsonObject timeout = new JsonObject();
		JsonObject peer = new JsonObject();
		peer.addProperty("endorser", 300);
		peer.addProperty("eventHub", 300);
		peer.addProperty("eventReg", 300);
		timeout.add("peer", peer);
		timeout.addProperty("orderer", 300);

		connection.add("timeout", timeout);
		client.add("connection", connection);

		client.addProperty("organization", clientOrg);
		return client;
	}

}
