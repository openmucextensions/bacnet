package bacnet4j;

import java.util.Date;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.service.unconfirmed.TimeSynchronizationRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.constructed.DateTime;

public class TimeSynchronization {

public static final int port = 0xBAC5;
	
	public static void main(String[] args) throws Throwable {

		IpNetwork network = new IpNetworkBuilder().port(port).build();
		Transport transport = new DefaultTransport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();
        
        @SuppressWarnings("deprecation")
		Date time = new Date(117, 01, 20);
        
        TimeSynchronizationRequest request = new TimeSynchronizationRequest(new DateTime(time.getTime()));
        // TimeSynchronizationRequest request = new TimeSynchronizationRequest(new DateTime(System.currentTimeMillis()));
        
        localDevice.sendGlobalBroadcast(request);
        System.out.println("Sended Time Synchronization Request with timestamp " + time.toString());
        
	}
}