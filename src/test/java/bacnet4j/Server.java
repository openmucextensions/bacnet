package bacnet4j;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.constructed.StatusFlags;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.Real;

public class Server {
	
	public static final int port = 0xBAC5;
	
	public static void main(String[] args) throws Throwable {
		
		// IpNetwork network = new IpNetwork("10.78.20.255", 0xBAC5);
		IpNetwork network = new IpNetworkBuilder().port(port).build();
		Transport transport = new DefaultTransport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();
        
        System.out.println("Local device is running with device id " + localDeviceID);
        
        ObjectIdentifier objectId = new ObjectIdentifier(ObjectType.analogValue, 1);

        // BACnetObject object = new BACnetObject(localDevice, objectId);
        BACnetObject object = new BACnetObject(objectId, "B'U'TOa");
        
        object.writeProperty(PropertyIdentifier.presentValue, new Real(12.3f));
        object.writeProperty(PropertyIdentifier.description, new CharacterString("Temperaturwert"));
        object.writeProperty(PropertyIdentifier.units, EngineeringUnits.degreesCelsius);
        object.writeProperty(PropertyIdentifier.statusFlags, new StatusFlags(false, false, false, false));
        object.writeProperty(PropertyIdentifier.eventState, EventState.normal);
        object.writeProperty(PropertyIdentifier.outOfService, new com.serotonin.bacnet4j.type.primitive.Boolean(false));

        localDevice.addObject(object);
 
		
	}
	
}
