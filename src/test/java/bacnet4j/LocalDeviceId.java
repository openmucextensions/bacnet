package bacnet4j;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;

public class LocalDeviceId {

	public static void main(String[] args) {
		
		IpNetwork network = new IpNetwork();
		Transport transport = new DefaultTransport(network);
		
		LocalDevice device = new LocalDevice(12345, transport);
		
		// get local device id
		System.out.println("Device id: " + device.getConfiguration().getInstanceId());
		
		device.terminate();
	}

}
