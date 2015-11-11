package bacnet4j;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.event.DeviceEventAdapter;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;

public class SearchDevices extends DeviceEventAdapter {

	public static void main(String[] args) throws Throwable {
		
		IpNetwork network = new IpNetwork(IpNetwork.DEFAULT_BROADCAST_IP, 0xBAC5);
        Transport transport = new DefaultTransport(network);
		
		// each BACnet device must have a unique instance number within the BACnet internetwork
		// according to BTL Device Implementation Guidelines,
		// the Device instance shall be configurable in the range of 0 to 4194302
		
		int localDeviceID = 10000 + (int) ( Math.random() * 10000);
		LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
		
		localDevice.initialize();
		localDevice.getEventHandler().addListener(new SearchDevices());
		
		System.out.println("Sending Who Is Request...");
		localDevice.sendGlobalBroadcast(new WhoIsRequest());
		
		Thread.sleep(5*1000);
		
		localDevice.terminate();

	}
	
	@Override
	public void iAmReceived(RemoteDevice remoteDevice) {
		
		System.out.println("I AM received:");
		System.out.println(remoteDevice.toExtendedString());
		System.out.println("Device ID: " + remoteDevice.getInstanceNumber());
		System.out.println("Address: " + remoteDevice.getAddress().getDescription());
		
	}
	
	

}
