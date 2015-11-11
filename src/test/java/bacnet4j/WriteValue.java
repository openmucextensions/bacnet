package bacnet4j;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkUtils;
import com.serotonin.bacnet4j.service.confirmed.WritePropertyRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.Real;

public class WriteValue {

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
 
        // ObjectIdentifier object = oids.get(0);
        ObjectIdentifier object = new ObjectIdentifier(ObjectType.analogOutput, 1);
                
        // priority is between 1 and 16, 1 is the highest
        // if priority is null, the value will be written to the property Relinquish_default, which has
        // to lowest priority (lower than 16)
        WritePropertyRequest wpr = new WritePropertyRequest(object, PropertyIdentifier.presentValue, null, new Real(50), null);
        localDevice.send(remoteDevice, wpr);
        
        // to release command in priority matrix write new Null()
        
        localDevice.terminate();
        
	}
	

}
