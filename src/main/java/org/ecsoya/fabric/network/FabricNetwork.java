/*
 *  Copyright 2017 DTCC, Fujitsu Australia Software Technology, IBM - All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ecsoya.fabric.network;

import static java.lang.String.format;
import static org.hyperledger.fabric.sdk.helper.Utils.isNullOrEmpty;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Channel.PeerOptions;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.Peer.PeerRole;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.NetworkConfigurationException;
import org.hyperledger.fabric.sdk.identity.X509Enrollment;
import org.yaml.snakeyaml.Yaml;

/**
 * Copy from @see {@link NetworkConfig}
 * 
 * Resolving entityMatcher to correct URLs.
 */
@SuppressWarnings("unchecked")
public class FabricNetwork {

	private final JsonObject jsonConfig;

	private FabricOrgInfo clientOrganization;

	private Map<String, FabricNetworkNode> orderers;
	private Map<String, FabricNetworkNode> peers;
	private Map<String, FabricNetworkNode> eventHubs;

	/**
	 * Names of Peers found
	 *
	 * @return Collection of peer names found.
	 */

	public Collection<String> getPeerNames() {
		if (peers == null) {
			return Collections.EMPTY_SET;
		} else {
			return new HashSet<>(peers.keySet());
		}
	}

	/**
	 * Names of Orderers found
	 *
	 * @return Collection of peer names found.
	 */
	public Collection<String> getOrdererNames() {
		if (orderers == null) {
			return Collections.EMPTY_SET;
		} else {
			return new HashSet<>(orderers.keySet());
		}
	}

	/**
	 * Names of EventHubs found
	 *
	 * @return Collection of eventhubs names found.
	 */

	public Collection<String> getEventHubNames() {
		if (eventHubs == null) {
			return Collections.EMPTY_SET;
		} else {
			return new HashSet<>(eventHubs.keySet());
		}
	}

	private Properties getNodeProperties(String type, String name, Map<String, FabricNetworkNode> nodes)
			throws InvalidArgumentException {
		if (isNullOrEmpty(name)) {
			throw new InvalidArgumentException("Parameter name is null or empty.");
		}

		FabricNetworkNode node = nodes.get(name);
		if (node == null) {
			throw new InvalidArgumentException(format("%s %s not found.", type, name));
		}

		if (null == node.getProperties()) {
			return new Properties();
		} else {

			return (Properties) node.getProperties().clone();
		}

	}

	private void setNodeProperties(String type, String name, Map<String, FabricNetworkNode> nodes,
			Properties properties) throws InvalidArgumentException {
		if (isNullOrEmpty(name)) {
			throw new InvalidArgumentException("Parameter name is null or empty.");
		}
		if (properties == null) {
			throw new InvalidArgumentException("Parameter properties is null.");
		}

		FabricNetworkNode node = nodes.get(name);
		if (node == null) {
			throw new InvalidArgumentException(format("%S %s not found.", type, name));
		}

		Properties ourCopyProps = new Properties();
		ourCopyProps.putAll(properties);

		node.setProperties(ourCopyProps);

	}

	/**
	 * Get properties for a specific peer.
	 *
	 * @param name Name of peer to get the properties for.
	 * @return The peer's properties.
	 * @throws InvalidArgumentException
	 */
	public Properties getPeerProperties(String name) throws InvalidArgumentException {
		return getNodeProperties("Peer", name, peers);

	}

	/**
	 * Get properties for a specific Orderer.
	 *
	 * @param name Name of orderer to get the properties for.
	 * @return The orderer's properties.
	 * @throws InvalidArgumentException
	 */
	public Properties getOrdererProperties(String name) throws InvalidArgumentException {
		return getNodeProperties("Orderer", name, orderers);

	}

	/**
	 * Get properties for a specific eventhub.
	 *
	 * @param name Name of eventhub to get the properties for.
	 * @return The eventhubs's properties.
	 * @throws InvalidArgumentException
	 */
	public Properties getEventHubsProperties(String name) throws InvalidArgumentException {
		return getNodeProperties("EventHub", name, eventHubs);

	}

	/**
	 * Set a specific peer's properties.
	 *
	 * @param name       The name of the peer's property to set.
	 * @param properties The properties to set.
	 * @throws InvalidArgumentException
	 */
	public void setPeerProperties(String name, Properties properties) throws InvalidArgumentException {
		setNodeProperties("Peer", name, peers, properties);
	}

	/**
	 * Set a specific orderer's properties.
	 *
	 * @param name       The name of the orderer's property to set.
	 * @param properties The properties to set.
	 * @throws InvalidArgumentException
	 */
	public void setOrdererProperties(String name, Properties properties) throws InvalidArgumentException {
		setNodeProperties("Orderer", name, orderers, properties);
	}

	/**
	 * Set a specific eventhub's properties.
	 *
	 * @param name       The name of the eventhub's property to set.
	 * @param properties The properties to set.
	 * @throws InvalidArgumentException
	 */
	public void setEventHubProperties(String name, Properties properties) throws InvalidArgumentException {
		setNodeProperties("EventHub", name, eventHubs, properties);
	}

	// Organizations, keyed on org name (and not on mspid!)
	private Map<String, FabricOrgInfo> organizations;

	private Map<String, EntityMatcher> entityMatchers;

	private static final Log logger = LogFactory.getLog(FabricNetwork.class);

	private FabricNetwork(JsonObject jsonConfig) throws InvalidArgumentException, NetworkConfigurationException {

		this.jsonConfig = jsonConfig;

		// Extract the main details
		String configName = getJsonValueAsString(jsonConfig.get("name"));
		if (configName == null || configName.isEmpty()) {
			throw new InvalidArgumentException("Network config must have a name");
		}

		String configVersion = getJsonValueAsString(jsonConfig.get("version"));
		if (configVersion == null || configVersion.isEmpty()) {
			throw new InvalidArgumentException("Network config must have a version");
			// TODO: Validate the version
		}

		// Preload entityMatchers
		JsonObject entityMatcherNode = getJsonObject(jsonConfig, "entityMatchers");
		loadEntityMatchers(entityMatcherNode);

		// Preload and create all peers, orderers, etc
		createAllPeers();
		createAllOrderers();

		Map<String, JsonObject> foundCertificateAuthorities = findCertificateAuthorities();
		// createAllCertificateAuthorities();
		createAllOrganizations(foundCertificateAuthorities);

		// Validate the organization for this client
		JsonObject jsonClient = getJsonObject(jsonConfig, "client");
		String orgName = jsonClient == null ? null : getJsonValueAsString(jsonClient.get("organization"));
		if (orgName == null || orgName.isEmpty()) {
			throw new InvalidArgumentException("A client organization must be specified");
		}

		clientOrganization = getOrganizationInfo(orgName);
		if (clientOrganization == null) {
			throw new InvalidArgumentException("Client organization " + orgName + " is not defined");
		}

	}

	private void loadEntityMatchers(JsonObject json) {
		entityMatchers = new HashMap<>();
		if (json == null) {
			return;
		}
		json.forEach((key, value) -> {
			if (value instanceof JsonArray) {
				((JsonArray) value).forEach(node -> {
					if (node instanceof JsonObject) {
						addEntityMatcher(entityMatchers, (JsonObject) node);
					}
				});
			} else if (value instanceof JsonObject) {
				addEntityMatcher(entityMatchers, (JsonObject) value);
			}
		});
	}

	private void addEntityMatcher(Map<String, EntityMatcher> entityMatchers, JsonObject json) {
		EntityMatcher entityMatcher = EntityMatcher.fromJson(json);
		if (entityMatcher == null || entityMatcher.mappedHost == null) {
			return;
		}
		entityMatchers.put(entityMatcher.mappedHost, entityMatcher);
	}

	/**
	 * "networkRoot" is the root directory of the network configurations.
	 * 
	 * 
	 * 
	 * @param networkRoot
	 * @param keyfilesPath
	 * @param org
	 * @return
	 * @throws Exception
	 */
	public static FabricNetwork build(File networkRoot, String keyfilesPath, String org) throws Exception {
		if (networkRoot == null || !networkRoot.exists()) {
			throw new Exception("Network root directory is not exist");
		}
		if (org == null) {
			throw new Exception("Orgnization name should not be empty.");
		}
		File configFileParent = new File(networkRoot, keyfilesPath);
		FabricNetwork network = null;

		File cfgYaml = new File(configFileParent, org + File.separator + "connection.yml");
		if (cfgYaml.exists()) {
			network = fromFile(cfgYaml, false);
		} else {
			// JSON file not generate the right 'pem', it's the path.
			File cfgJson = new File(configFileParent, org + File.separator + "connection.json");
			if (cfgJson.exists()) {
				network = fromFile(cfgJson, true);
			} else {
				throw new Exception("Network config file can not be found: connection.json or connection.yml");
			}
		}
		network.parsePemBytesFrom(networkRoot);
		network.resolvePeerAdmin(networkRoot, keyfilesPath);
		return network;
	}

	/**
	 * Creates a new NetworkConfig instance configured with details supplied in a
	 * YAML file.
	 *
	 * @param configFile The file containing the network configuration
	 * @return A new NetworkConfig instance
	 * @throws InvalidArgumentException
	 * @throws IOException
	 */
	public static FabricNetwork fromYamlFile(File configFile)
			throws InvalidArgumentException, IOException, NetworkConfigurationException {
		return fromFile(configFile, false);
	}

	/**
	 * Creates a new NetworkConfig instance configured with details supplied in a
	 * JSON file.
	 *
	 * @param configFile The file containing the network configuration
	 * @return A new NetworkConfig instance
	 * @throws InvalidArgumentException
	 * @throws IOException
	 */
	public static FabricNetwork fromJsonFile(File configFile)
			throws InvalidArgumentException, IOException, NetworkConfigurationException {
		return fromFile(configFile, true);
	}

	/**
	 * Creates a new NetworkConfig instance configured with details supplied in YAML
	 * format
	 *
	 * @param configStream A stream opened on a YAML document containing network
	 *                     configuration details
	 * @return A new NetworkConfig instance
	 * @throws InvalidArgumentException
	 */
	public static FabricNetwork fromYamlStream(InputStream configStream)
			throws InvalidArgumentException, NetworkConfigurationException {

		logger.trace("NetworkConfig.fromYamlStream...");

		// Sanity check
		if (configStream == null) {
			throw new InvalidArgumentException("configStream must be specified");
		}

		Yaml yaml = new Yaml();

		Map<String, Object> map = yaml.load(configStream);

		JsonObjectBuilder builder = Json.createObjectBuilder(map);

		JsonObject jsonConfig = builder.build();
		return fromJsonObject(jsonConfig);
	}

	/**
	 * Creates a new NetworkConfig instance configured with details supplied in JSON
	 * format
	 *
	 * @param configStream A stream opened on a JSON document containing network
	 *                     configuration details
	 * @return A new NetworkConfig instance
	 * @throws InvalidArgumentException
	 */
	public static FabricNetwork fromJsonStream(InputStream configStream)
			throws InvalidArgumentException, NetworkConfigurationException {

		logger.trace("NetworkConfig.fromJsonStream...");

		// Sanity check
		if (configStream == null) {
			throw new InvalidArgumentException("configStream must be specified");
		}

		// Read the input stream and convert to JSON

		try (JsonReader reader = Json.createReader(configStream)) {
			JsonObject jsonConfig = (JsonObject) reader.read();
			return fromJsonObject(jsonConfig);
		}

	}

	/**
	 * Creates a new NetworkConfig instance configured with details supplied in a
	 * JSON object
	 *
	 * @param jsonConfig JSON object containing network configuration details
	 * @return A new NetworkConfig instance
	 * @throws InvalidArgumentException
	 */
	public static FabricNetwork fromJsonObject(JsonObject jsonConfig)
			throws InvalidArgumentException, NetworkConfigurationException {

		// Sanity check
		if (jsonConfig == null) {
			throw new InvalidArgumentException("jsonConfig must be specified");
		}

		if (logger.isTraceEnabled()) {
			logger.trace(format("NetworkConfig.fromJsonObject: %s", jsonConfig.toString()));
		}

		return FabricNetwork.load(jsonConfig);
	}

	// Loads a NetworkConfig object from a Json or Yaml file
	private static FabricNetwork fromFile(File configFile, boolean isJson)
			throws InvalidArgumentException, IOException, NetworkConfigurationException {

		// Sanity check
		if (configFile == null) {
			throw new InvalidArgumentException("configFile must be specified");
		}

		if (logger.isTraceEnabled()) {
			logger.trace(format("NetworkConfig.fromFile: %s  isJson = %b", configFile.getAbsolutePath(), isJson));
		}

		FabricNetwork config;

		// Json file
		try (InputStream stream = new FileInputStream(configFile)) {
			config = isJson ? fromJsonStream(stream) : fromYamlStream(stream);
		}

		return config;
	}

	/**
	 * Returns a new NetworkConfig instance and populates it from the specified JSON
	 * object
	 *
	 * @param jsonConfig The JSON object containing the config details
	 * @return A populated NetworkConfig instance
	 * @throws InvalidArgumentException
	 */
	private static FabricNetwork load(JsonObject jsonConfig)
			throws InvalidArgumentException, NetworkConfigurationException {

		// Sanity check
		if (jsonConfig == null) {
			throw new InvalidArgumentException("config must be specified");
		}

		return new FabricNetwork(jsonConfig);
	}

	public FabricOrgInfo getClientOrganization() {
		return clientOrganization;
	}

	public FabricOrgInfo getOrganizationInfo(String orgName) {
		return organizations.get(orgName);
	}

	public Collection<FabricOrgInfo> getOrganizationInfos() {
		return Collections.unmodifiableCollection(organizations.values());
	}

	/**
	 * Returns the admin user associated with the client organization
	 *
	 * @return The admin user details
	 * @throws NetworkConfigurationException
	 */
	public FabricUserInfo getPeerAdmin() throws NetworkConfigurationException {
		// Get the details from the client organization
		return getPeerAdmin(clientOrganization.getName());
	}

	/**
	 * Returns the admin user associated with the specified organization
	 *
	 * @param orgName The name of the organization
	 * @return The admin user details
	 * @throws NetworkConfigurationException
	 */
	public FabricUserInfo getPeerAdmin(String orgName) throws NetworkConfigurationException {

		FabricOrgInfo org = getOrganizationInfo(orgName);
		if (org == null) {
			throw new NetworkConfigurationException(format("Organization %s is not defined", orgName));
		}

		return org.getPeerAdmin();
	}

	/**
	 * Returns a channel configured using the details in the Network Configuration
	 * file
	 *
	 * @param client      The associated client
	 * @param channelName The name of the channel
	 * @return A configured Channel instance
	 */
	public Channel loadChannel(HFClient client, String channelName) throws NetworkConfigurationException {

		if (logger.isTraceEnabled()) {
			logger.trace(format("NetworkConfig.loadChannel: %s", channelName));
		}

		Channel channel = null;

		JsonObject channels = getJsonObject(jsonConfig, "channels");

		if (channels != null) {
			JsonObject jsonChannel = getJsonObject(channels, channelName);
			if (jsonChannel != null) {
				channel = client.getChannel(channelName);
				if (channel != null) {
					// The channel already exists in the client!
					// Note that by rights this should never happen as
					// HFClient.loadChannelFromConfig should have already checked for this!
					throw new NetworkConfigurationException(
							format("Channel %s is already configured in the client!", channelName));
				}
				channel = reconstructChannel(client, channelName, jsonChannel);
			} else {

				final Set<String> channelNames = getChannelNames();
				if (channelNames.isEmpty()) {
					throw new NetworkConfigurationException("Channel configuration has no channels defined.");
				}
				final StringBuilder sb = new StringBuilder(1000);

				channelNames.forEach(s -> {
					if (sb.length() != 0) {
						sb.append(", ");
					}
					sb.append(s);
				});
				throw new NetworkConfigurationException(
						format("Channel %s not found in configuration file. Found channel names: %s ", channelName,
								sb.toString()));

			}

		} else {
			throw new NetworkConfigurationException("Channel configuration has no channels defined.");
		}

		return channel;
	}

	// Creates Node instances representing all the orderers defined in the config
	// file
	private void createAllOrderers() throws NetworkConfigurationException {

		// Sanity check
		if (orderers != null) {
			throw new NetworkConfigurationException("INTERNAL ERROR: orderers has already been initialized!");
		}

		orderers = new HashMap<>();

		// orderers is a JSON object containing a nested object for each orderers
		JsonObject jsonOrderers = getJsonObject(jsonConfig, "orderers");

		if (jsonOrderers != null) {

			for (Entry<String, JsonValue> entry : jsonOrderers.entrySet()) {
				String ordererName = entry.getKey();

				JsonObject jsonOrderer = getJsonValueAsObject(entry.getValue());
				if (jsonOrderer == null) {
					throw new NetworkConfigurationException(
							format("Error loading config. Invalid orderer entry: %s", ordererName));
				}

				FabricNetworkNode orderer = createNode(ordererName, jsonOrderer, "url");
				if (orderer == null) {
					throw new NetworkConfigurationException(
							format("Error loading config. Invalid orderer entry: %s", ordererName));
				}
				orderers.put(ordererName, orderer);
			}
		}

	}

	// Creates Node instances representing all the peers (and associated event hubs)
	// defined in the config file
	private void createAllPeers() throws NetworkConfigurationException {

		// Sanity checks
		if (peers != null) {
			throw new NetworkConfigurationException("INTERNAL ERROR: peers has already been initialized!");
		}

		if (eventHubs != null) {
			throw new NetworkConfigurationException("INTERNAL ERROR: eventHubs has already been initialized!");
		}

		peers = new HashMap<>();
		eventHubs = new HashMap<>();

		// peers is a JSON object containing a nested object for each peer
		JsonObject jsonPeers = getJsonObject(jsonConfig, "peers");

		// out("Peers: " + (jsonPeers == null ? "null" : jsonPeers.toString()));
		if (jsonPeers != null) {

			for (Entry<String, JsonValue> entry : jsonPeers.entrySet()) {
				String peerName = entry.getKey();

				JsonObject jsonPeer = getJsonValueAsObject(entry.getValue());
				if (jsonPeer == null) {
					throw new NetworkConfigurationException(
							format("Error loading config. Invalid peer entry: %s", peerName));
				}

				FabricNetworkNode peer = createNode(peerName, jsonPeer, "url");
				if (peer == null) {
					throw new NetworkConfigurationException(
							format("Error loading config. Invalid peer entry: %s", peerName));
				}
				peers.put(peerName, peer);

				// Also create an event hub with the same name as the peer
				FabricNetworkNode eventHub = createNode(peerName, jsonPeer, "eventUrl"); // may not be present
				if (null != eventHub) {
					eventHubs.put(peerName, eventHub);
				}
			}
		}

	}

	// Produce a map from tag to jsonobject for the CA
	private Map<String, JsonObject> findCertificateAuthorities() throws NetworkConfigurationException {
		Map<String, JsonObject> ret = new HashMap<>();

		JsonObject jsonCertificateAuthorities = getJsonObject(jsonConfig, "certificateAuthorities");
		if (null != jsonCertificateAuthorities) {

			for (Entry<String, JsonValue> entry : jsonCertificateAuthorities.entrySet()) {
				String name = entry.getKey();

				JsonObject jsonCA = getJsonValueAsObject(entry.getValue());
				if (jsonCA == null) {
					throw new NetworkConfigurationException(format("Error loading config. Invalid CA entry: %s", name));
				}
				ret.put(name, jsonCA);
			}
		}

		return ret;

	}

	// Creates JsonObjects representing all the Organizations defined in the config
	// file
	private void createAllOrganizations(Map<String, JsonObject> foundCertificateAuthorities)
			throws NetworkConfigurationException {

		// Sanity check
		if (organizations != null) {
			throw new NetworkConfigurationException("INTERNAL ERROR: organizations has already been initialized!");
		}

		organizations = new HashMap<>();

		// organizations is a JSON object containing a nested object for each Org
		JsonObject jsonOrganizations = getJsonObject(jsonConfig, "organizations");

		if (jsonOrganizations != null) {

			for (Entry<String, JsonValue> entry : jsonOrganizations.entrySet()) {
				String orgName = entry.getKey();

				JsonObject jsonOrg = getJsonValueAsObject(entry.getValue());
				if (jsonOrg == null) {
					throw new NetworkConfigurationException(
							format("Error loading config. Invalid Organization entry: %s", orgName));
				}

				FabricOrgInfo org = createOrg(orgName, jsonOrg, foundCertificateAuthorities);
				organizations.put(orgName, org);
			}
		}

	}

	// Reconstructs an existing channel
	private Channel reconstructChannel(HFClient client, String channelName, JsonObject jsonChannel)
			throws NetworkConfigurationException {

		Channel channel = null;

		try {
			channel = client.newChannel(channelName);

			// orderers is an array of orderer name strings
			JsonArray ordererNames = getJsonValueAsArray(jsonChannel.get("orderers"));
			boolean foundOrderer = false;

			// out("Orderer names: " + (ordererNames == null ? "null" :
			// ordererNames.toString()));
			if (ordererNames != null) {
				for (JsonValue jsonVal : ordererNames) {

					String ordererName = getJsonValueAsString(jsonVal);
					Orderer orderer = getOrderer(client, ordererName);
					if (orderer == null) {
						throw new NetworkConfigurationException(
								format("Error constructing channel %s. Orderer %s not defined in configuration",
										channelName, ordererName));
					}
					channel.addOrderer(orderer);
					foundOrderer = true;
				}
			}

			// peers is an object containing a nested object for each peer
			JsonObject jsonPeers = getJsonObject(jsonChannel, "peers");
			boolean foundPeer = false;

			// out("Peers: " + (peers == null ? "null" : peers.toString()));
			if (jsonPeers != null) {

				for (Entry<String, JsonValue> entry : jsonPeers.entrySet()) {
					String peerName = entry.getKey();

					if (logger.isTraceEnabled()) {
						logger.trace(format("NetworkConfig.reconstructChannel: Processing peer %s", peerName));
					}

					JsonObject jsonPeer = getJsonValueAsObject(entry.getValue());
					if (jsonPeer == null) {
						throw new NetworkConfigurationException(
								format("Error constructing channel %s. Invalid peer entry: %s", channelName, peerName));
					}

					Peer peer = getPeer(client, peerName);
					if (peer == null) {
						throw new NetworkConfigurationException(
								format("Error constructing channel %s. Peer %s not defined in configuration",
										channelName, peerName));
					}

					// Set the various roles
					PeerOptions peerOptions = PeerOptions.createPeerOptions();

					for (PeerRole peerRole : PeerRole.values()) {
						setPeerRole(channelName, peerOptions, jsonPeer, peerRole);
					}

					foundPeer = true;

					// Add the event hub associated with this peer
					EventHub eventHub = getEventHub(client, peerName);
					if (eventHub != null) {
						channel.addEventHub(eventHub);
						if (haveNoPeerRoles(peerOptions)) { // means no roles were found but there is an event hub so
															// define all roles but eventing.
							peerOptions.setPeerRoles(EnumSet.of(PeerRole.ENDORSING_PEER, PeerRole.CHAINCODE_QUERY,
									PeerRole.LEDGER_QUERY));
						}
					}
					channel.addPeer(peer, peerOptions);

				}

			}

			if (!foundPeer) {
				// peers is a required field
				throw new NetworkConfigurationException(
						format("Error constructing channel %s. At least one peer must be specified", channelName));
			}

		} catch (InvalidArgumentException e) {
			throw new IllegalArgumentException(e);
		}

		return channel;
	}

	private boolean haveNoPeerRoles(PeerOptions peerOptions) {
		if (peerOptions == null) {
			return false;
		}
		try {
			Field f = PeerOptions.class.getDeclaredField("peerRoles");
			f.setAccessible(true);
			return f.get(peerOptions) == null;
		} catch (Exception e) {
			return false;
		}
	}

	private static void setPeerRole(String channelName, PeerOptions peerOptions, JsonObject jsonPeer, PeerRole role)
			throws NetworkConfigurationException {
		String propName = roleNameRemap(role);
		JsonValue val = jsonPeer.get(propName);
		if (val != null) {
			Boolean isSet = getJsonValueAsBoolean(val);
			if (isSet == null) {
				// This is an invalid boolean value
				throw new NetworkConfigurationException(
						format("Error constructing channel %s. Role %s has invalid boolean value: %s", channelName,
								propName, val.toString()));
			}
			if (isSet) {
				peerOptions.addPeerRole(role);
			}
		}
	}

	@SuppressWarnings("serial")
	private static Map<PeerRole, String> roleNameRemapHash = new HashMap<PeerRole, String>() {
		{
			put(PeerRole.SERVICE_DISCOVERY, "discover");
		}
	};

	private static String roleNameRemap(PeerRole peerRole) {
		String remap = roleNameRemapHash.get(peerRole);
		return remap == null ? peerRole.getPropertyName() : remap;
	}

	// Returns a new Orderer instance for the specified orderer name
	public Orderer getOrderer(HFClient client, String ordererName) throws InvalidArgumentException {
		Orderer orderer = null;
		FabricNetworkNode o = orderers.get(ordererName);
		if (o != null) {
			orderer = client.newOrderer(o.getName(), o.getUrl(), o.getProperties());
		}
		return orderer;
	}

	// Creates a new Node instance from a JSON object
	private FabricNetworkNode createNode(String nodeName, JsonObject jsonNode, String urlPropName)
			throws NetworkConfigurationException {

//        jsonNode.
//        if (jsonNode.isNull(urlPropName)) {
//            return  null;
//        }

		String url = jsonNode.getString(urlPropName, null);
		if (url == null) {
			return null;
		}

		String grpcUrl = null;

		Properties props = extractProperties(jsonNode, "grpcOptions");

		if (null != props) {
			String value = props.getProperty("grpc.keepalive_time_ms");
			if (null != value) {
				props.remove("grpc.keepalive_time_ms");
				props.put("grpc.NettyChannelBuilderOption.keepAliveTime",
						new Object[] { new Long(value), TimeUnit.MILLISECONDS });
			}

			value = props.getProperty("grpc.keepalive_timeout_ms");
			if (null != value) {
				props.remove("grpc.keepalive_timeout_ms");
				props.put("grpc.NettyChannelBuilderOption.keepAliveTimeout",
						new Object[] { new Long(value), TimeUnit.MILLISECONDS });
			}

			value = props.getProperty("ssl-target-name-override");
			if (null != value) {
				EntityMatcher entityMatcher = entityMatchers.get(value);
				if (entityMatcher != null) {
					if ("url".equals(urlPropName)) {
						grpcUrl = entityMatcher.getUrl(value);
					} else if ("eventUrl".equals(urlPropName)) {
						grpcUrl = entityMatcher.getEventUrl(value);
					}
				}

			}
		}

		// Extract the pem details
		getTLSCerts(nodeName, jsonNode, props);

		return new FabricNetworkNode(nodeName, url, grpcUrl, props);
	}

	private void getTLSCerts(String nodeName, JsonObject jsonOrderer, Properties props) {
		JsonObject jsonTlsCaCerts = getJsonObject(jsonOrderer, "tlsCACerts");
		if (jsonTlsCaCerts != null) {
			String pemFilename = getJsonValueAsString(jsonTlsCaCerts.get("path"));
			String pemBytes = getJsonValueAsString(jsonTlsCaCerts.get("pem"));

			if (pemFilename != null) {
				// let the sdk handle non existing errors could be they don't exist during
				// parsing but are there later.
				props.put("pemFile", pemFilename);
			}

			if (pemBytes != null) {
				props.put("pemBytes", pemBytes.getBytes());
			}
		}
	}

	// Creates a new OrgInfo instance from a JSON object
	private FabricOrgInfo createOrg(String orgName, JsonObject jsonOrg,
			Map<String, JsonObject> foundCertificateAuthorities) throws NetworkConfigurationException {

		String msgPrefix = format("Organization %s", orgName);

		String mspId = getJsonValueAsString(jsonOrg.get("mspid"));

		FabricOrgInfo org = new FabricOrgInfo(orgName, mspId);

		String cryptoPath = getJsonValueAsString(jsonOrg.get("cryptoPath"));
		if (cryptoPath != null) {
			org.setCryptoPath(cryptoPath);
		}
		// Peers
		JsonArray jsonPeers = getJsonValueAsArray(jsonOrg.get("peers"));
		if (jsonPeers != null) {
			for (JsonValue peer : jsonPeers) {
				String peerName = getJsonValueAsString(peer);
				if (peerName != null) {
					org.addPeerName(peerName);
				}
			}
		}

		// CAs
		JsonArray jsonCertificateAuthorities = getJsonValueAsArray(jsonOrg.get("certificateAuthorities"));
		if (jsonCertificateAuthorities != null) {
			for (JsonValue jsonCA : jsonCertificateAuthorities) {

				String caName = getJsonValueAsString(jsonCA);

				if (caName != null) {
					JsonObject jsonObject = foundCertificateAuthorities.get(caName);
					if (jsonObject != null) {
						org.addCertificateAuthority(createCA(caName, jsonObject, org));
					} else {
//						throw new NetworkConfigurationException(
//								format("%s: Certificate Authority %s is not defined", msgPrefix, caName));
					}
				}
			}
		}

		String adminPrivateKeyString = extractPemString(jsonOrg, "adminPrivateKey", msgPrefix);
		String signedCert = extractPemString(jsonOrg, "signedCert", msgPrefix);

		if (!isNullOrEmpty(adminPrivateKeyString) && !isNullOrEmpty(signedCert)) {

			PrivateKey privateKey = null;

			try {
				privateKey = getPrivateKeyFromString(adminPrivateKeyString);
			} catch (IOException ioe) {
				throw new NetworkConfigurationException(format("%s: Invalid private key", msgPrefix), ioe);
			}

			final PrivateKey privateKeyFinal = privateKey;

			try {
				org.peerAdmin = new FabricUserInfo(mspId, "PeerAdmin_" + mspId + "_" + orgName, null);
			} catch (Exception e) {
				throw new NetworkConfigurationException(e.getMessage(), e);
			}
			org.peerAdmin.setEnrollment(new X509Enrollment(privateKeyFinal, signedCert));

		}

		return org;
	}

	private static PrivateKey getPrivateKeyFromString(String data) throws IOException {

		final Reader pemReader = new StringReader(data);

		final PrivateKeyInfo pemPair;
		try (PEMParser pemParser = new PEMParser(pemReader)) {
			pemPair = (PrivateKeyInfo) pemParser.readObject();
		}

		return new JcaPEMKeyConverter().getPrivateKey(pemPair);
	}

	// Returns the PEM (as a String) from either a path or a pem field
	private static String extractPemString(JsonObject json, String fieldName, String msgPrefix)
			throws NetworkConfigurationException {

		String path = null;
		String pemString = null;

		JsonObject jsonField = getJsonValueAsObject(json.get(fieldName));
		if (jsonField != null) {
			path = getJsonValueAsString(jsonField.get("path"));
			pemString = getJsonValueAsString(jsonField.get("pem"));
		}

		if (path != null && pemString != null) {
			throw new NetworkConfigurationException(
					format("%s should not specify both %s path and pem", msgPrefix, fieldName));
		}

		if (path != null) {
			// Determine full pathname and ensure the file exists
			File pemFile = new File(path);
			String fullPathname = pemFile.getAbsolutePath();
			if (!pemFile.exists()) {
				throw new NetworkConfigurationException(
						format("%s: %s file %s does not exist", msgPrefix, fieldName, fullPathname));
			}
			try (FileInputStream stream = new FileInputStream(pemFile)) {
				pemString = IOUtils.toString(stream, "UTF-8");
			} catch (IOException ioe) {
				throw new NetworkConfigurationException(format("Failed to read file: %s", fullPathname), ioe);
			}

		}

		return pemString;
	}

	// Creates a new CAInfo instance from a JSON object
	private FabricCAInfo createCA(String name, JsonObject jsonCA, FabricOrgInfo org)
			throws NetworkConfigurationException {

		String url = getJsonValueAsString(jsonCA.get("url"));
		Properties httpOptions = extractProperties(jsonCA, "httpOptions");

		String enrollId = null;
		String enrollSecret = null;

		List<JsonObject> registrars = getJsonValueAsList(jsonCA.get("registrar"));
		List<FabricUserInfo> regUsers = new LinkedList<>();
		if (registrars != null) {

			for (JsonObject reg : registrars) {
				enrollId = getJsonValueAsString(reg.get("enrollId"));
				enrollSecret = getJsonValueAsString(reg.get("enrollSecret"));
				try {
					regUsers.add(new FabricUserInfo(org.getMspId(), enrollId, enrollSecret));
				} catch (Exception e) {
					throw new NetworkConfigurationException(e.getMessage(), e);
				}
			}
		}

		FabricCAInfo caInfo = new FabricCAInfo(name, org.getMspId(), url, regUsers, httpOptions);

		String caName = getJsonValueAsString(jsonCA.get("caName"));
		if (caName != null) {
			caInfo.setCaName(caName);
		}

		Properties properties = new Properties();
		if (null != httpOptions && "false".equals(httpOptions.getProperty("verify"))) {
			properties.setProperty("allowAllHostNames", "true");
		}
		getTLSCerts(name, jsonCA, properties);
		caInfo.setProperties(properties);

		return caInfo;
	}

	// Extracts all defined properties of the specified field and returns a
	// Properties object
	private static Properties extractProperties(JsonObject json, String fieldName) {
		Properties props = new Properties();

		// Extract any other grpc options
		JsonObject options = getJsonObject(json, fieldName);
		if (options != null) {

			for (Entry<String, JsonValue> entry : options.entrySet()) {
				String key = entry.getKey();
				JsonValue value = entry.getValue();
				props.setProperty(key, getJsonValue(value));
			}
		}
		return props;
	}

	// Returns a new Peer instance for the specified peer name
	public Peer getPeer(HFClient client, String peerName) throws InvalidArgumentException {
		Peer peer = null;
		FabricNetworkNode p = peers.get(peerName);
		if (p != null) {
			peer = client.newPeer(p.getName(), p.getUrl(), p.getProperties());
		}
		return peer;
	}

	// Returns a new EventHub instance for the specified name
	public EventHub getEventHub(HFClient client, String name) throws InvalidArgumentException {
		EventHub ehub = null;
		FabricNetworkNode e = eventHubs.get(name);
		if (e != null) {
			ehub = client.newEventHub(e.getName(), e.getUrl(), e.getProperties());
		}
		return ehub;
	}

	// Returns the specified JsonValue in a suitable format
	// If it's a JsonString - it returns the string
	// If it's a number = it returns the string representation of that number
	// If it's TRUE or FALSE - it returns "true" and "false" respectively
	// If it's anything else it returns null
	private static String getJsonValue(JsonValue value) {
		String s = null;
		if (value != null) {
			s = getJsonValueAsString(value);
			if (s == null) {
				s = getJsonValueAsNumberString(value);
			}
			if (s == null) {
				Boolean b = getJsonValueAsBoolean(value);
				if (b != null) {
					s = b ? "true" : "false";
				}
			}
		}
		return s;
	}

	// Returns the specified JsonValue as a JsonObject, or null if it's not an
	// object
	private static JsonObject getJsonValueAsObject(JsonValue value) {
		return (value != null && value.getValueType() == ValueType.OBJECT) ? value.asJsonObject() : null;
	}

	// Returns the specified JsonValue as a JsonArray, or null if it's not an array
	private static JsonArray getJsonValueAsArray(JsonValue value) {
		return (value != null && value.getValueType() == ValueType.ARRAY) ? value.asJsonArray() : null;
	}

	// Returns the specified JsonValue as a List. Allows single or array
	private static List<JsonObject> getJsonValueAsList(JsonValue value) {
		if (value != null) {
			if (value.getValueType() == ValueType.ARRAY) {
				return value.asJsonArray().getValuesAs(JsonObject.class);

			} else if (value.getValueType() == ValueType.OBJECT) {
				List<JsonObject> ret = new ArrayList<>();
				ret.add(value.asJsonObject());

				return ret;
			}
		}
		return null;
	}

	// Returns the specified JsonValue as a String, or null if it's not a string
	private static String getJsonValueAsString(JsonValue value) {
		return (value != null && value.getValueType() == ValueType.STRING) ? ((JsonString) value).getString() : null;
	}

	// Returns the specified JsonValue as a String, or null if it's not a string
	private static String getJsonValueAsNumberString(JsonValue value) {
		return (value != null && value.getValueType() == ValueType.NUMBER) ? value.toString() : null;
	}

	// Returns the specified JsonValue as a Boolean, or null if it's not a boolean
	private static Boolean getJsonValueAsBoolean(JsonValue value) {
		if (value != null) {
			if (value.getValueType() == ValueType.TRUE) {
				return true;
			} else if (value.getValueType() == ValueType.FALSE) {
				return false;
			}
		}
		return null;
	}

	// Returns the specified property as a JsonObject
	private static JsonObject getJsonObject(JsonObject object, String propName) {
		JsonObject obj = null;
		JsonValue val = object.get(propName);
		if (val != null && val.getValueType() == ValueType.OBJECT) {
			obj = val.asJsonObject();
		}
		return obj;
	}

	/**
	 * Get the channel names found.
	 *
	 * @return A set of the channel names found in the configuration file or empty
	 *         set if none found.
	 */

	public Set<String> getChannelNames() {
		Set<String> ret = Collections.EMPTY_SET;

		JsonObject channels = getJsonObject(jsonConfig, "channels");
		if (channels != null) {
			final Set<String> channelNames = channels.keySet();
			if (channelNames != null && !channelNames.isEmpty()) {
				ret = new HashSet<>(channelNames);
			}
		}
		return ret;
	}

	public boolean resolvePeerAdmin(File networkRoot, String keyfilesPath) {
		if (networkRoot == null || !networkRoot.exists()) {
			return false;
		}
		if (clientOrganization == null) {
			return false;
		}
		if (clientOrganization.peerAdmin != null) {
			return true;
		}
		String cryptoPath = clientOrganization.getCryptoPath();
		if (cryptoPath == null) {
			return false;
		}
		cryptoPath = cryptoPath.replaceAll("\\{username\\}", "Admin");

		File root = new File(new File(networkRoot, keyfilesPath), cryptoPath);
		if (!root.exists()) {
			return false;
		}
		File keyFile = getFirstChild(new File(root, "keystore"));
		if (keyFile == null) {
			return false;
		}
		PrivateKey privateKey = null;

		try {
			privateKey = getPrivateKeyFromString(new String(Files.readAllBytes(keyFile.toPath())));
		} catch (IOException e) {
			return false;
		}
		File certFile = getFirstChild(new File(root, "admincerts"));
		if (certFile == null) {
			return false;
		}
		String certificate = null;
		try {
			certificate = new String(Files.readAllBytes(certFile.toPath()));
		} catch (IOException e) {
			return false;
		}
		String mspId = clientOrganization.getMspId();
		String name = clientOrganization.getName();
		try {
			clientOrganization.peerAdmin = new FabricUserInfo(mspId, "PeerAdmin_" + mspId + "_" + name, null);
		} catch (Exception e) {
			return false;
		}
		clientOrganization.peerAdmin.setEnrollment(new X509Enrollment(privateKey, certificate));
		return true;
	}

	private File getFirstChild(File dir) {
		if (dir == null || !dir.exists()) {
			return null;
		}
		File[] listFiles = dir.listFiles();
		if (listFiles.length == 0) {
			return null;
		}
		return listFiles[0];
	}

	/**
	 * 
	 * Replace pemFile to pemBytes
	 * 
	 */
	public boolean parsePemBytesFrom(File networkRoot) {
		if (networkRoot == null || !networkRoot.exists()) {
			return false;
		}
		if (orderers != null && !orderers.isEmpty()) {
			orderers.values().stream().forEach(node -> {
				replacePemContents(node, networkRoot);
			});
		}
		if (peers != null && !peers.isEmpty()) {
			peers.values().stream().forEach(node -> {
				replacePemContents(node, networkRoot);
			});
		}
		if (eventHubs != null && !eventHubs.isEmpty()) {
			eventHubs.values().stream().forEach(node -> {
				replacePemContents(node, networkRoot);
			});
		}
		if (clientOrganization != null) {
			clientOrganization.getCertificateAuthorities().stream().forEach(ca -> {
				Properties properties = ca.getProperties();
				String pemFile = properties.getProperty("pemFile");
				File file = new File(pemFile);
				if (file.exists()) {
					return;
				}
				file = new File(networkRoot, pemFile);
				if (file.exists()) {
					try {
						byte[] pemBytes = Files.readAllBytes(file.toPath());
						properties.put("pemBytes", pemBytes);
						properties.remove("pemFile");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
		}
		return true;
	}

	private void replacePemContents(FabricNetworkNode node, File certificateRootDir) {
		if (node == null || node.getProperties() == null) {
			return;
		}
		if (!node.getProperties().containsKey("pemFile")) {
			return;
		}
		String pemFile = node.getProperties().getProperty("pemFile");
		File file = new File(pemFile);
		if (file.exists()) {
			return;
		}
		file = new File(certificateRootDir, pemFile);
		if (file.exists()) {
			try {
				byte[] pemBytes = Files.readAllBytes(file.toPath());
				node.getProperties().put("pemBytes", pemBytes);
				node.getProperties().remove("pemFile");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public FabricNetworkNode getPeerNode(String name) {
		if (peers == null || peers.isEmpty() || name == null) {
			return null;
		}
		return peers.get(name);
	}

	public FabricNetworkNode getOrdererNode(String name) {
		if (orderers == null || orderers.isEmpty() || name == null) {
			return null;
		}
		return orderers.get(name);
	}

	public FabricNetworkNode getEventHubNode(String name) {
		if (eventHubs == null || eventHubs.isEmpty() || name == null) {
			return null;
		}
		return eventHubs.get(name);
	}

	public static class EntityMatcher {
		String mappedHost;
		String pattern;
		String sslTargetOverrideUrlSubstitutionExp;
		String urlSubstitutionExp;
		String eventUrlSubstitutionExp;

		EntityMatcher() {
		}

		public String getSslTargetOverrideUrl(String host) {
			if (!matches(host)) {
				return null;
			}
			return sslTargetOverrideUrlSubstitutionExp;
		}

		public String getUrl(String host) {
			if (!matches(host)) {
				return null;
			}
			return urlSubstitutionExp;
		}

		public String getEventUrl(String host) {
			if (!matches(host)) {
				return null;
			}
			return eventUrlSubstitutionExp;
		}

		public boolean matches(String host) {
			if (host == null) {
				return false;
			}
			if (host.equals(mappedHost)) {
				return true;
			}
			if (pattern != null) {
				// check pattern first?
				return host.matches(pattern);
			}
			return false;
		}

		public static EntityMatcher fromJson(JsonObject json) {
			if (json == null) {
				return null;
			}
			EntityMatcher entityMatcher = new EntityMatcher();
			List<String> initializedKeys = new ArrayList<>();
			json.forEach((key, value) -> {
				String str = null;
				if (value instanceof JsonString) {
					str = ((JsonString) value).getString();
				}
				if (str == null) {
					return;
				}
				try {
					Field f = EntityMatcher.class.getDeclaredField(key);
					f.setAccessible(true);
					f.set(entityMatcher, str);
					initializedKeys.add(key);
				} catch (Exception e) {
					e.printStackTrace();
				}

			});
			if (initializedKeys.isEmpty()) {
				return null;
			}

			return entityMatcher;
		}
	}

}
