package org.openmuc.extensions.driver.bacnet;

import java.util.List;

import org.openmuc.extensions.driver.bacnet.BACnetConnection;
import org.openmuc.framework.config.ChannelScanInfo;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.constructed.Address;

public class ScanForChannels {
	
	private static int port = 0xBAC5;
	private static String remoteDeviceIpAddress = "10.78.20.115";
	private static int remoteDeviceIdentifier = 2138113;
	
	public static void main(String[] args) throws Throwable {
		
		IpNetwork network = new IpNetwork(IpNetwork.DEFAULT_BROADCAST_IP, port);
        Transport transport = new Transport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();

        RemoteDevice remoteDevice = localDevice.findRemoteDevice(new Address(remoteDeviceIpAddress, port), null, remoteDeviceIdentifier);
        
        BACnetConnection connection = new BACnetConnection(localDevice, remoteDevice);
        
        long startTime = System.currentTimeMillis();
        List<ChannelScanInfo> channelScanInfos = connection.scanForChannels(null);
        long endTime = System.currentTimeMillis();
        
        System.out.println("Found " + channelScanInfos.size() + " channels:");
        
        for (ChannelScanInfo channelScanInfo : channelScanInfos) {
			System.out.println(channelScanInfo.getChannelAddress() + " (" + channelScanInfo.getDescription() + ")");
		}
        
        long executionTime = endTime - startTime;
        System.out.println("\nExecution time " + executionTime + "ms");
        
        localDevice.terminate();
		
	}
	
}
