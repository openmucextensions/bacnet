package bacnet4j;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.util.DiscoveryUtils;
import com.serotonin.bacnet4j.util.RequestUtils;

public class SearchDevicesWithoutCallback {

	private static int port = 0xBAC5;
	
	public static void main(String[] args) throws Exception {
		
		IpNetwork network = new IpNetwork(IpNetwork.DEFAULT_BROADCAST_IP, port);
        Transport transport = new DefaultTransport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();
        
        localDevice.sendGlobalBroadcast(new WhoIsRequest());
        Thread.sleep(1*1000);	// wait a bit to collect the response messages
        
        for (RemoteDevice device : localDevice.getRemoteDevices()) {
			DiscoveryUtils.getExtendedDeviceInformation(localDevice, device);
        	System.out.println("Found: " + device.getInstanceNumber() + " (" + device.getName() + ")");
		}
        
        localDevice.terminate();

	}

}
