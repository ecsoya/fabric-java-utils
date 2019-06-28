package org.ecsoya.fabric.tests;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Properties;

import org.ecsoya.fabric.network.FabricConnection;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.NetworkConfigurationException;

public class LoadConnectionTest {

	public static void main(String[] args) {
		File configFile = new File("src/test/resources/connection/connection.yml");

		try {
			FabricConnection fconn = FabricConnection.fromYamlFile(configFile);

			Collection<String> peerNames = fconn.getPeerNames();
			for (String name : peerNames) {
				System.out.println("Peer: " + name);
				Properties peerProperties = fconn.getPeerProperties(name);
				System.out.println(peerProperties);
			}

			System.out.println(fconn);
		} catch (InvalidArgumentException | NetworkConfigurationException | IOException e) {
			e.printStackTrace();
		}
	}

}
