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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.ecsoya.fabric.network.FabricCAInfo;
import org.ecsoya.fabric.network.FabricNetwork;
import org.ecsoya.fabric.network.FabricOrgInfo;
import org.ecsoya.fabric.network.FabricUserInfo;
import org.ecsoya.fabric.user.UserContext;
import org.ecsoya.fabric.util.FabricUtil;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.InstallProposalRequest;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.NetworkConfigurationException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

/**
 * Wrapper class for HFClient.
 * 
 */

public class FabricClient {
	private static final byte[] EXPECTED_EVENT_DATA = "!".getBytes(UTF_8);
	private static final String EXPECTED_EVENT_NAME = "event";
	private static final long DEFAULT_PROPOSAL_WAIT_TIME = 3000;
	private final HFClient instance;
	private FabricNetwork network;

	private CAClient defaultCAClient;

	private ChannelClient channelClient;

	public FabricClient(FabricNetwork network) throws Exception {
		this(network, createClientInstance());
	}

	public FabricClient(FabricNetwork network, HFClient core) throws Exception {
		this.instance = core;
		this.network = network;
		initialize();
	}

	public FabricNetwork getNetwork() {
		return network;
	}

	private static HFClient createClientInstance()
			throws IllegalAccessException, InstantiationException, ClassNotFoundException, CryptoException,
			InvalidArgumentException, NoSuchMethodException, InvocationTargetException {
		CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
		// setup the client
		HFClient instance = HFClient.createNewInstance();
		instance.setCryptoSuite(cryptoSuite);
		return instance;
	}

	public void switchNetwork(FabricNetwork network) throws Exception {
		this.network = network;
		this.defaultCAClient = null;
		initialize();
	}

	private void initialize() throws Exception {
		if (network == null) {
			return;
		}
		FabricOrgInfo orgInfo = network.getClientOrganization();
		if (orgInfo != null) {
			FabricUserInfo peerAdmin = orgInfo.getPeerAdmin();
			if (peerAdmin != null) {
				instance.setUserContext(peerAdmin);
			}

			CAClient caClient = getDefaultCAClient();
			if (caClient != null) {
				caClient.setAdminUserContext(peerAdmin);
			}
		}
	}

	public void setupChannelClient(String channelName)
			throws NetworkConfigurationException, InvalidArgumentException, TransactionException {
		if (channelClient != null && channelName != null && channelName.equals(channelClient.getName())) {
			return;
		}
		if (channelName != null && network != null) {
			channelClient = loadChannelClient(channelName);
		}
	}

	public void setChannelClient(ChannelClient channelClient) {
		this.channelClient = channelClient;
	}

	public ChannelClient getChannelClient() {
		return channelClient;
	}

	public List<String> getPeerNames() {
		if (network == null || network.getClientOrganization() == null) {
			return Collections.emptyList();
		}
		return network.getClientOrganization().getPeerNames();
	}

	public List<Peer> getPeers() {
		if (channelClient == null) {
			return Collections.emptyList();
		}
		List<String> peerNames = getPeerNames();
		return channelClient.getChannel().getPeers().stream().filter(peer -> {
			return peerNames.contains(peer.getName());
		}).collect(Collectors.toList());
	}

	public void setUserContext(User userContext) throws InvalidArgumentException {
		getInstance().setUserContext(userContext);
	}

	public void setUserContext(String affiliation, String mspId, String username, String password) throws Exception {
		UserContext userContext = FabricUtil.readUserContext(affiliation, username);
		if (userContext == null) {
			userContext = new UserContext();
			userContext.setAffiliation(affiliation);
			userContext.setMspId(mspId);
			userContext.setName(username);
			CAClient caClient = getDefaultCAClient();
			if (caClient != null) {
				setUserContext(caClient.enrollAdminUser(username, password));
			}

		}
		setUserContext(userContext);

	}

	/**
	 * Return an instance of HFClient.
	 * 
	 * @return
	 */
	public HFClient getInstance() {
		return instance;
	}

	public CAClient getDefaultCAClient() {
		if (defaultCAClient == null && network != null) {
			try {
				FabricOrgInfo orgInfo = network.getClientOrganization();
				if (orgInfo != null) {
					List<FabricCAInfo> certificateAuthorities = orgInfo.getCertificateAuthorities();
					if (!certificateAuthorities.isEmpty()) {
						FabricCAInfo defaultCAInfo = certificateAuthorities.get(0);
						defaultCAClient = new CAClient(defaultCAInfo);
					}
				}
			} catch (Exception e) {
				defaultCAClient = null;
			}
		}
		return defaultCAClient;
	}

	public ChannelClient loadChannelClient(String name)
			throws NetworkConfigurationException, InvalidArgumentException, TransactionException {
		if (name == null) {
			return null;
		}
		ChannelClient channelClient = null;
		Channel channel = instance.getChannel(name);
		if (channel != null) {
			channelClient = new ChannelClient(name, channel, this);
		}
		if (channelClient == null && network != null) {
			channel = network.loadChannel(instance, name);
			channel.initialize();
			channelClient = new ChannelClient(name, channel, this);
		}
		setChannelClient(channelClient);
		return channelClient;
	}

	public ChannelClient createChannelClient(String name) throws InvalidArgumentException {
		Channel channel = instance.newChannel(name);
		return new ChannelClient(name, channel, this);
	}

	public ChannelClient createChannelClient(String channelName, String[] peers, String... orderers)
			throws InvalidArgumentException, TransactionException {
		Channel channel = instance.getChannel(channelName);

		if (channel != null) {
			channel.shutdown(true);
		}
		channel = instance.newChannel(channelName);
		if (network != null) {
			if (peers != null) {
				for (String peerName : peers) {
					Peer peer = network.getPeer(instance, peerName);
					if (peer != null) {
						channel.addPeer(peer);
					}
					EventHub eventHub = network.getEventHub(instance, peerName);
					if (eventHub != null) {
						channel.addEventHub(eventHub);
					}
				}
			}
			if (orderers != null) {
				for (String ordererName : orderers) {
					Orderer orderer = network.getOrderer(instance, ordererName);
					if (orderer != null) {
						channel.addOrderer(orderer);
					}
				}
			}
			channel.initialize();
		}

		return new ChannelClient(channelName, channel, this);
	}

	public Peer loadPeer(String name) throws InvalidArgumentException {
		if (name == null || network == null) {
			return null;
		}
		return network.getPeer(instance, name);
	}

	public EventHub loadEventHub(String name) throws InvalidArgumentException {
		if (name == null || network == null) {
			return null;
		}
		return network.getEventHub(instance, name);
	}

	public Orderer loadOrderer(String name) throws InvalidArgumentException {
		if (name == null || network == null) {
			return null;
		}
		return network.getOrderer(instance, name);
	}

	public TransactionProposalRequest newTransactionProposalRequest(String chaincode, String function, String... args)
			throws InvalidArgumentException {
		TransactionProposalRequest request = instance.newTransactionProposalRequest();
		ChaincodeID ccid = ChaincodeID.newBuilder().setName(chaincode).build();

		request.setChaincodeID(ccid);
		request.setFcn(function);
		request.setArgs(args);
		request.setProposalWaitTime(DEFAULT_PROPOSAL_WAIT_TIME);

		Map<String, byte[]> tm2 = new HashMap<>();
		tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8));
		tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8));
		tm2.put("result", ":)".getBytes(UTF_8));
		tm2.put(EXPECTED_EVENT_NAME, EXPECTED_EVENT_DATA);
		request.setTransientMap(tm2);
		return request;
	}

	public QueryByChaincodeRequest newQueryByChangcodeRequest(String chaincode, String function, String... args)
			throws InvalidArgumentException {
		QueryByChaincodeRequest queryRequest = instance.newQueryProposalRequest();
		ChaincodeID ccid = ChaincodeID.newBuilder().setName(chaincode).build();
		queryRequest.setChaincodeID(ccid); // ChaincodeId object as created in Invoke block
		queryRequest.setFcn(function); // Chaincode function name for querying the blocks

		if (args != null) {
			queryRequest.setArgs(args);
		}
		Map<String, byte[]> tm2 = new HashMap<>();
		tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
		tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
		tm2.put("result", ":)".getBytes(UTF_8));
		tm2.put(EXPECTED_EVENT_NAME, EXPECTED_EVENT_DATA);
		return queryRequest;
	}

	/**
	 * Deploy chain code.
	 * 
	 * @param chainCodeName
	 * @param chaincodePath
	 * @param codepath
	 * @param language
	 * @param version
	 * @param peers
	 * @return
	 * @throws InvalidArgumentException
	 * @throws IOException
	 * @throws ProposalException
	 */
	public Collection<ProposalResponse> deployJavaChainCode(String chainCodeName, File chaincodeSourceLocation,
			String version, Collection<Peer> peers) throws InvalidArgumentException, IOException, ProposalException {
		InstallProposalRequest request = instance.newInstallProposalRequest();
		ChaincodeID.Builder chaincodeIDBuilder = ChaincodeID.newBuilder().setName(chainCodeName).setVersion(version);
//				.setPath(chaincodeSourceLocation.getPath());
		ChaincodeID chaincodeID = chaincodeIDBuilder.build();
		Logger.getLogger(FabricClient.class.getName()).log(Level.INFO,
				"Deploying chaincode " + chainCodeName + " using Fabric client " + instance.getUserContext().getMspId()
						+ " " + instance.getUserContext().getName());
		request.setChaincodeID(chaincodeID);
		request.setUserContext(instance.getUserContext());
		request.setChaincodeSourceLocation(chaincodeSourceLocation);
		request.setChaincodeVersion(version);
		request.setChaincodeLanguage(Type.JAVA);
		Collection<ProposalResponse> responses = instance.sendInstallProposal(request, peers);
		return responses;
	}

	public Collection<ProposalResponse> deployChainCode(String chainCodeName, String chaincodePath, String codepath,
			String language, String version, Collection<Peer> peers)
			throws InvalidArgumentException, IOException, ProposalException {
		InstallProposalRequest request = instance.newInstallProposalRequest();
		ChaincodeID.Builder chaincodeIDBuilder = ChaincodeID.newBuilder().setName(chainCodeName).setVersion(version)
				.setPath(chaincodePath);
		ChaincodeID chaincodeID = chaincodeIDBuilder.build();
		Logger.getLogger(FabricClient.class.getName()).log(Level.INFO,
				"Deploying chaincode " + chainCodeName + " using Fabric client " + instance.getUserContext().getMspId()
						+ " " + instance.getUserContext().getName());
		request.setChaincodeID(chaincodeID);
		request.setUserContext(instance.getUserContext());
		request.setChaincodeSourceLocation(new File(codepath));
		request.setChaincodeVersion(version);
		Collection<ProposalResponse> responses = instance.sendInstallProposal(request, peers);
		return responses;
	}
}
