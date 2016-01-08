package bacnet4j;

import java.util.List;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkUtils;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyAck;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedRequestService;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.util.RequestUtils;

public class GetObjectIDs {

	private static int port = 0xBAC5;
	private static String remoteDeviceIpAddress = "10.78.20.115";
	private static int remoteDeviceIdentifier = 2138113;
	
	
	public static void main(String[] args) throws Throwable {
		
		// IpNetwork network = new IpNetwork(IpNetwork.DEFAULT_BROADCAST_IP, port);
        IpNetwork network = new IpNetworkBuilder().port(port).build();
		
		Transport transport = new DefaultTransport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();

        RemoteDevice remoteDevice = localDevice.findRemoteDevice(IpNetworkUtils.toAddress(remoteDeviceIpAddress, port), remoteDeviceIdentifier);
        
        System.out.println("Reading object list from remote device " + remoteDevice.getInstanceNumber() + "...");
        System.out.println("");
        
        @SuppressWarnings("unchecked")
		List<ObjectIdentifier> oids = ((SequenceOf<ObjectIdentifier>) RequestUtils.sendReadPropertyAllowNull(
                localDevice, remoteDevice, remoteDevice.getObjectIdentifier(), PropertyIdentifier.objectList)).getValues();
                
        for (ObjectIdentifier objectIdentifier : oids) {
			
        	System.out.println("Object identifier: " + objectIdentifier.toString());
			
        	// read property object name from each object
			ConfirmedRequestService request = new ReadPropertyRequest(objectIdentifier, PropertyIdentifier.objectName);
			ReadPropertyAck result = localDevice.send(remoteDevice, request).get();
			
			System.out.println("Object name: " + result.getValue().toString());
			System.out.println("");
		}
        
        localDevice.terminate();
	}

}
