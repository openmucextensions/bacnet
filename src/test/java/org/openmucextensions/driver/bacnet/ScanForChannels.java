package org.openmucextensions.driver.bacnet;

import java.util.List;

import org.openmuc.framework.config.ChannelScanInfo;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkUtils;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;

public class ScanForChannels {
	
	private static int port = 0xBAC5;
	private static String remoteDeviceIpAddress = "192.168.1.15";
	private static int remoteDeviceIdentifier = 2098177;
	
	public static void main(String[] args) throws Throwable {
		
		IpNetwork network = new IpNetwork(IpNetwork.DEFAULT_BROADCAST_IP, port);
        Transport transport = new DefaultTransport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();

        RemoteDevice remoteDevice = localDevice.findRemoteDevice(IpNetworkUtils.toAddress(remoteDeviceIpAddress, port), remoteDeviceIdentifier);
        
        BACnetRemoteConnection connection = new BACnetRemoteConnection(localDevice, remoteDevice);
        
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
