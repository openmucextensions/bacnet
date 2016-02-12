package bacnet4j;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.util.DiscoveryUtils;

public class DiscoverDevice {

	public static final int port = 0xBAC5;
	
	public static void main(String[] args) throws Throwable {
		
		int deviceId = 2138113;
		
		// IpNetwork network = new IpNetwork("10.78.20.255", 0xBAC5);
		IpNetwork network = new IpNetworkBuilder().port(port).build();
		Transport transport = new DefaultTransport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();	

        RemoteDevice device = DiscoveryUtils.discoverDevice(localDevice, deviceId);
        
        if(device!=null) System.out.println("found device " + device.getInstanceNumber());
        else System.out.println("could not find device " + deviceId);
        
        localDevice.terminate();
	}

}
