package bacnet4j;

import java.util.List;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkUtils;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.util.PropertyReferences;
import com.serotonin.bacnet4j.util.PropertyValues;
import com.serotonin.bacnet4j.util.RequestUtils;

public class MultipleRead2 {

	public static void main(String[] args) throws Throwable {
		
		IpNetwork network = new IpNetwork(IpNetwork.DEFAULT_BROADCAST_IP, 0xBAC5);
        Transport transport = new DefaultTransport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();

        RemoteDevice remoteDevice = localDevice.findRemoteDevice(IpNetworkUtils.toAddress("10.78.20.115", 0xbac5), 2138113);

        // RequestUtils.getExtendedDeviceInformation(localDevice, remoteDevice);
        
    	@SuppressWarnings("unchecked")
		List<ObjectIdentifier> oids = ((SequenceOf<ObjectIdentifier>) RequestUtils.sendReadPropertyAllowNull(
                localDevice, remoteDevice, remoteDevice.getObjectIdentifier(), PropertyIdentifier.objectList)).getValues();

    	PropertyReferences references = new PropertyReferences();
    	
    	for (ObjectIdentifier objectIdentifier : oids) {
			references.add(objectIdentifier, PropertyIdentifier.presentValue);
		}
    	
    	PropertyValues values = RequestUtils.readProperties(localDevice, remoteDevice, references, null);
    	
    	for (ObjectIdentifier objectIdentifier : oids) {
			System.out.println(values.getString(objectIdentifier, PropertyIdentifier.presentValue));
		}
    	
    	localDevice.terminate();
	}

}
