package org.openmucextensions.driver.bacnet;

import java.util.List;

import org.openmuc.framework.config.ChannelScanInfo;
import org.openmuc.framework.driver.spi.Connection;
import org.openmucextensions.driver.bacnet.BACnetDriver;

public class TestDriverConnection {
	
	public static void main(String[] args) throws Throwable {
		
		String settings = "localDevicePort=0xBAC5;remoteDevicePort=0xBAC5";
		String deviceAddress = "2138113";
		BACnetDriver driver = new BACnetDriver();
		
		Connection connection = driver.connect(deviceAddress, settings);
		List<ChannelScanInfo> channels = connection.scanForChannels(null);
		
		for (ChannelScanInfo channelScanInfo : channels) {
			System.out.println("Found channel " + channelScanInfo.getChannelAddress());
		}
		
		connection.disconnect();

	}

}
