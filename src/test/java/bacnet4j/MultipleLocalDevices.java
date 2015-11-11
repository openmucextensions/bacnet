package bacnet4j;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;

public class MultipleLocalDevices {

	public static void main(String[] args) {
		
		// this example shows that it's possible to create multiple local devices
		// with different IP port numbers (and instance numbers)
		
		IpNetwork network1 = new IpNetwork(IpNetwork.DEFAULT_BROADCAST_IP, 0xBAC0);
		Transport transport1 = new DefaultTransport(network1);
		LocalDevice localDevice1 = new LocalDevice(10000, transport1);
		System.out.println("Local device with port 0xBAC0 created");
		
		IpNetwork network2 = new IpNetwork(IpNetwork.DEFAULT_BROADCAST_IP, 0xBAC1);
		Transport transport2 = new DefaultTransport(network2);
		LocalDevice localDevice2 = new LocalDevice(10001, transport2);
		System.out.println("Local device with port 0xBAC1 created");

		localDevice1.terminate();
		localDevice2.terminate();
		
	}

}
