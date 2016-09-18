package org.openmucextensions.driver.bacnet;

import java.util.ArrayList;
import java.util.List;

import org.openmuc.framework.config.ChannelScanInfo;
import org.openmuc.framework.driver.spi.ChannelRecordContainer;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkUtils;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;

public class ReadChannels {

	private static int port = 0xBAC5;
	private static String remoteDeviceIpAddress = "10.78.20.115";
	private static int remoteDeviceIdentifier = 2138113;
	
	public static void main(String[] args) throws Throwable {
		
		IpNetwork network = new IpNetwork(IpNetwork.DEFAULT_BROADCAST_IP, port);
        Transport transport = new DefaultTransport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();

        RemoteDevice remoteDevice = localDevice.findRemoteDevice(IpNetworkUtils.toAddress(remoteDeviceIpAddress, port), remoteDeviceIdentifier);
        
        BACnetRemoteConnection connection = new BACnetRemoteConnection(localDevice, remoteDevice);
        
        List<ChannelRecordContainer> containers = new ArrayList<ChannelRecordContainer>();
        
        // scan for channels
        List<ChannelScanInfo> channelScanInfos = connection.scanForChannels(null);
        
        // create containers
        for (ChannelScanInfo channelScanInfo : channelScanInfos) {
			containers.add(new ChannelRecordContainerImpl(channelScanInfo.getChannelAddress()));
		}
        
        // perform readout
        long startTime = System.currentTimeMillis();
        connection.read(containers, null, null);
        long endTime = System.currentTimeMillis();
        
        for (ChannelRecordContainer channelRecordContainer : containers) {
			System.out.println(channelRecordContainer.getChannelAddress());
			System.out.println(channelRecordContainer.getRecord().toString());
		}
        
        long executionTime = endTime - startTime;
        System.out.println("\nExecution time " + executionTime + "ms");
        
        localDevice.terminate();
	}

}
