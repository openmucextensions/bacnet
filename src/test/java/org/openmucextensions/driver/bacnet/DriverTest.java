package org.openmucextensions.driver.bacnet;

import java.util.ArrayList;
import java.util.List;

import org.openmuc.framework.config.ChannelScanInfo;
import org.openmuc.framework.config.DeviceScanInfo;
import org.openmuc.framework.driver.spi.Connection;
import org.openmuc.framework.driver.spi.DriverDeviceScanListener;
import org.openmucextensions.driver.bacnet.BACnetDriver;

public class DriverTest implements DriverDeviceScanListener {

	public static final String settings = "discoverySleepTime=2000;scanPort=0xBAC5";
	
	private BACnetDriver driver;
	private List<String> devices = new ArrayList<String>();
	
	public static void main(String[] args) throws Throwable {
		new DriverTest().start();
	}
	
	public void start() throws Throwable {
				
		driver = new BACnetDriver();
		driver.activate(null, null);
		
		System.out.println("Scanning for devices...");
		driver.scanForDevices(settings, this);
		
		for (String deviceAddress : devices) {
			
			System.out.println("\nScanning for channels from device " + deviceAddress);
			Connection connection = driver.connect(deviceAddress, "port=0xBAC5;instanceNumber=10011");
			List<ChannelScanInfo> channelInfos = connection.scanForChannels(settings);
			for (ChannelScanInfo channelScanInfo : channelInfos) {
				System.out.println(channelScanInfo.getChannelAddress());
			}
			System.out.println("Found " + channelInfos.size() + " channel(s)");
			connection.disconnect();
		}
		
		driver.deactivate(null);
		
	}

	@Override
	public void deviceFound(DeviceScanInfo scanInfo) {
		System.out.println("Found device: " + scanInfo.getDeviceAddress());
		devices.add(scanInfo.getDeviceAddress());
	}

	@Override
	public void scanProgressUpdate(int progress) {
		System.out.println("Scan progress: " + progress + "%");
	}
	
}
