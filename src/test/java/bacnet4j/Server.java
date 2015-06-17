package bacnet4j;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.Real;

public class Server {
	
	public static void main(String[] args) throws Throwable {
		
		IpNetwork network = new IpNetwork("10.78.20.255", 0xBAC5);
        Transport transport = new Transport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();
        
        System.out.println("Local device is running with device id " + localDeviceID);
        
        ObjectIdentifier objectId = new ObjectIdentifier(ObjectType.analogValue, 1);
        BACnetObject object = new BACnetObject(localDevice, objectId);
        
        object.setProperty(new PropertyValue(PropertyIdentifier.presentValue, new Real(12.3f)));
        object.setProperty(new PropertyValue(PropertyIdentifier.description, new CharacterString("Temperaturwert")));
        object.setProperty(new PropertyValue(PropertyIdentifier.units, EngineeringUnits.degreesCelsius));
        object.setProperty(new PropertyValue(PropertyIdentifier.objectName, new CharacterString("B'U'TOa")));
        
        
        localDevice.addObject(object);
 
		
	}
	
}
