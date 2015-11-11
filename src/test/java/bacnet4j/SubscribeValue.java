package bacnet4j;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.RemoteObject;
import com.serotonin.bacnet4j.event.DeviceEventListener;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.service.confirmed.SubscribeCOVRequest;
import com.serotonin.bacnet4j.service.confirmed.ReinitializeDeviceRequest.ReinitializedStateOfDevice;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.constructed.Choice;
import com.serotonin.bacnet4j.type.constructed.DateTime;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.constructed.TimeStamp;
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.EventType;
import com.serotonin.bacnet4j.type.enumerated.MessagePriority;
import com.serotonin.bacnet4j.type.enumerated.NotifyType;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.notificationParameters.NotificationParameters;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

public class SubscribeValue {
	
	private static int port = 0xBAC5;
	private static String remoteDeviceIpAddress = "10.78.20.115";
	private static int remoteDeviceIdentifier = 2138113;
	
	public static void main(String[] args) throws Throwable {
		
		IpNetwork network = new IpNetwork(IpNetwork.DEFAULT_BROADCAST_IP, port);
        Transport transport = new DefaultTransport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();

// TODO
//        localDevice.getEventHandler().addListener(new DeviceEventListener() {
//			
//			@Override
//			public void textMessageReceived(RemoteDevice textMessageSourceDevice,
//					Choice messageClass, MessagePriority messagePriority,
//					CharacterString message) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void synchronizeTime(DateTime dateTime, boolean utc) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void reinitializeDevice(
//					ReinitializedStateOfDevice reinitializedStateOfDevice) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void propertyWritten(BACnetObject obj, PropertyValue pv) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void privateTransferReceived(UnsignedInteger vendorId,
//					UnsignedInteger serviceNumber, Encodable serviceParameters) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void listenerException(Throwable e) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void iHaveReceived(RemoteDevice d, RemoteObject o) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void iAmReceived(RemoteDevice d) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void eventNotificationReceived(UnsignedInteger processIdentifier,
//					RemoteDevice initiatingDevice,
//					ObjectIdentifier eventObjectIdentifier, TimeStamp timeStamp,
//					UnsignedInteger notificationClass, UnsignedInteger priority,
//					EventType eventType, CharacterString messageText,
//					NotifyType notifyType, Boolean ackRequired, EventState fromState,
//					EventState toState, NotificationParameters eventValues) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void covNotificationReceived(
//					UnsignedInteger subscriberProcessIdentifier,
//					RemoteDevice initiatingDevice,
//					ObjectIdentifier monitoredObjectIdentifier,
//					UnsignedInteger timeRemaining,
//					SequenceOf<PropertyValue> listOfValues) {
//				
//				System.out.println("Received notification: " + listOfValues);
//				System.out.println("Time remaining: " + timeRemaining);
//			}
//			
//			@Override
//			public boolean allowPropertyWrite(BACnetObject obj, PropertyValue pv) {
//				// TODO Auto-generated method stub
//				return false;
//			}
//		});
//
//        RemoteDevice remoteDevice = localDevice.findRemoteDevice(new Address(remoteDeviceIpAddress, port), null, remoteDeviceIdentifier);
// 
//        ObjectIdentifier object = new ObjectIdentifier(ObjectType.analogOutput, 1);
//        
//        UnsignedInteger subscriberProcessIdentifier = new UnsignedInteger(0);
//        UnsignedInteger lifetime = new UnsignedInteger(0);
//        
//        SubscribeCOVRequest request = new SubscribeCOVRequest(subscriberProcessIdentifier, object, new Boolean(true), lifetime);
//        localDevice.send(remoteDevice, request);
//        
//        System.out.println("Waiting for notifications...");
		
	}
	
}
