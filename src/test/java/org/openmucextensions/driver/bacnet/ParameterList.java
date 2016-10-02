package org.openmucextensions.driver.bacnet;

import java.util.List;

import org.openmuc.framework.config.ChannelScanInfo;

public class ParameterList {

	private final static String deviceAddress = "2138113;10.78.20.115";
	private final static String settings = "broadcastIP=255.255.255.255;devicePort=47813";
	
	public static void main(String[] args) throws Throwable {
		
		BACnetDriver driver = new BACnetDriver();
		BACnetRemoteConnection connection = (BACnetRemoteConnection) driver.connect(deviceAddress, settings);

		List<ChannelScanInfo> scanInfos = connection.scanForChannels("parameterlist");
		
		for (ChannelScanInfo channelScanInfo : scanInfos) {
			System.out.println("Found channel " + channelScanInfo.getChannelAddress() + " with description " + channelScanInfo.getDescription());
		}
		
		connection.disconnect();
		
	}

}
