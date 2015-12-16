/*  OpenMUC BACnet driver service
 *  Copyright (C) 2015 Mike Pichler
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.openmucextensions.driver.bacnet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.openmuc.framework.config.ArgumentSyntaxException;
import org.openmuc.framework.config.ChannelScanInfo;
import org.openmuc.framework.config.ScanException;
import org.openmuc.framework.data.BooleanValue;
import org.openmuc.framework.data.Flag;
import org.openmuc.framework.data.FloatValue;
import org.openmuc.framework.data.IntValue;
import org.openmuc.framework.data.Record;
import org.openmuc.framework.data.StringValue;
import org.openmuc.framework.data.Value;
import org.openmuc.framework.data.ValueType;
import org.openmuc.framework.driver.spi.ChannelRecordContainer;
import org.openmuc.framework.driver.spi.ChannelValueContainer;
import org.openmuc.framework.driver.spi.Connection;
import org.openmuc.framework.driver.spi.ConnectionException;
import org.openmuc.framework.driver.spi.RecordsReceivedListener;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.RemoteObject;
import com.serotonin.bacnet4j.ServiceFuture;
import com.serotonin.bacnet4j.event.DeviceEventListener;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.PropertyValueException;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyMultipleAck;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedRequestService;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyMultipleRequest;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyRequest;
import com.serotonin.bacnet4j.service.confirmed.ReinitializeDeviceRequest.ReinitializedStateOfDevice;
import com.serotonin.bacnet4j.service.confirmed.SubscribeCOVRequest;
import com.serotonin.bacnet4j.service.confirmed.WritePropertyRequest;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.constructed.Choice;
import com.serotonin.bacnet4j.type.constructed.DateTime;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.ReadAccessResult;
import com.serotonin.bacnet4j.type.constructed.ReadAccessResult.Result;
import com.serotonin.bacnet4j.type.constructed.ReadAccessSpecification;
import com.serotonin.bacnet4j.type.constructed.Sequence;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.constructed.TimeStamp;
import com.serotonin.bacnet4j.type.enumerated.BinaryPV;
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.EventType;
import com.serotonin.bacnet4j.type.enumerated.MessagePriority;
import com.serotonin.bacnet4j.type.enumerated.NotifyType;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.notificationParameters.NotificationParameters;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.Null;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.PropertyReferences;
import com.serotonin.bacnet4j.util.PropertyValues;
import com.serotonin.bacnet4j.util.RequestUtils;

/**
 * BACnet/IP communication class that implements reading, writing and listening
 * for values of a specified remote device. Also, all channels (data points) with
 * compatible object types of the remote device can be retrieved.
 * 
 * @author Mike Pichler
 *
 */
public class BACnetConnection implements Connection, DeviceEventListener {
	
	private final LocalDevice LOCAL_DEVICE;
	private final RemoteDevice REMOTE_DEVICE;
	
	private final UnsignedInteger subscriberProcessIdentifier = new UnsignedInteger(0);
	
	// BACnet write priority between 1 and 16 or null for relinquish_default
	private Integer writePriority = null;
	
	private final Map<ObjectType, ObjectTypeInfo> acceptedTypes;
	private Map<String, ObjectIdentifier> objectHandles = null;
	
	private Map<ObjectIdentifier, ChannelRecordContainer> covContainers = new ConcurrentHashMap<ObjectIdentifier, ChannelRecordContainer>();
	private RecordsReceivedListener recordsReceivedListener = null;
	
	/**
	 * Constructs a new <code>BACnetConnection</code> object for the specified remote device.
	 * 
	 * @param localDevice the local device instance to communicate with
	 * @param remoteDevice the remote device instance to communicate with
	 */
	public BACnetConnection(LocalDevice localDevice, RemoteDevice remoteDevice) {
		LOCAL_DEVICE = localDevice;
		REMOTE_DEVICE = remoteDevice;
		
		// accepted object types (only this object types will be provided as channel to the OpenMUC framework)
		// the related ObjectTypeInfo class specifies the OpenMUC behavior
		acceptedTypes = new HashMap<ObjectType, ObjectTypeInfo>();
		// analog objects are of type REAL in BACnet (32 bit, float)
		acceptedTypes.put(ObjectType.analogInput, new ObjectTypeInfo(ValueType.FLOAT, null, Boolean.TRUE, Boolean.FALSE));
		acceptedTypes.put(ObjectType.analogOutput, new ObjectTypeInfo(ValueType.FLOAT, null, Boolean.TRUE, Boolean.TRUE));
		acceptedTypes.put(ObjectType.analogValue, new ObjectTypeInfo(ValueType.FLOAT, null, Boolean.TRUE, Boolean.TRUE));
		acceptedTypes.put(ObjectType.binaryInput, new ObjectTypeInfo(ValueType.BOOLEAN, null, Boolean.TRUE, Boolean.FALSE));
		acceptedTypes.put(ObjectType.binaryOutput, new ObjectTypeInfo(ValueType.BOOLEAN, null, Boolean.TRUE, Boolean.TRUE));
		acceptedTypes.put(ObjectType.binaryValue, new ObjectTypeInfo(ValueType.BOOLEAN, null, Boolean.TRUE, Boolean.TRUE));
		acceptedTypes.put(ObjectType.multiStateInput, new ObjectTypeInfo(ValueType.INTEGER, null, Boolean.TRUE, Boolean.FALSE));
		acceptedTypes.put(ObjectType.multiStateOutput, new ObjectTypeInfo(ValueType.INTEGER, null, Boolean.TRUE, Boolean.TRUE));
		acceptedTypes.put(ObjectType.multiStateValue, new ObjectTypeInfo(ValueType.INTEGER, null, Boolean.TRUE, Boolean.TRUE));
	
		LOCAL_DEVICE.getEventHandler().addListener(this);
	
	}
	

	@Override
	public List<ChannelScanInfo> scanForChannels(String settings)
			throws UnsupportedOperationException, ArgumentSyntaxException, ScanException, ConnectionException {
		
		if(LOCAL_DEVICE == null) throw new ConnectionException("Local device instance is null");
		if(REMOTE_DEVICE == null) throw new ConnectionException("Remote device instance is null");
		
		if(!testConnection()) throw new ConnectionException("Remote device " + REMOTE_DEVICE.getInstanceNumber() + " is not reachable");
		
		List<ChannelScanInfo> channelScanInfos = new ArrayList<ChannelScanInfo>();
		
		objectHandles = new HashMap<String, ObjectIdentifier>();
		
		try {
			
			// get object list from remote device
			@SuppressWarnings("unchecked")
			List<ObjectIdentifier> objectIdentifiers = ((SequenceOf<ObjectIdentifier>) RequestUtils.sendReadPropertyAllowNull(
					LOCAL_DEVICE, REMOTE_DEVICE, REMOTE_DEVICE.getObjectIdentifier(), PropertyIdentifier.objectList)).getValues();
		
			// filter object identifiers to just get accepted ones (see constructor)
			objectIdentifiers = getAcceptedObjects(objectIdentifiers);
			
			// request name and description for each accepted object
			for (ObjectIdentifier objectIdentifier : objectIdentifiers) {
				
				List<ReadAccessSpecification> specifications = new ArrayList<ReadAccessSpecification>();
				specifications.add(new ReadAccessSpecification(objectIdentifier, PropertyIdentifier.objectName));
				specifications.add(new ReadAccessSpecification(objectIdentifier, PropertyIdentifier.description));
				
				SequenceOf<ReadAccessSpecification> sequence = new SequenceOf<ReadAccessSpecification>(specifications);
		        ConfirmedRequestService serviceRequest = new ReadPropertyMultipleRequest(sequence);
		        
		        ServiceFuture future = LOCAL_DEVICE.send(REMOTE_DEVICE, serviceRequest);
		        ReadPropertyMultipleAck ack = future.get();
		        
		        SequenceOf<ReadAccessResult> results = ack.getListOfReadAccessResults();
		        
		        String channelAddress = "";
		        String description = "";
		        
		        for (ReadAccessResult readAccessResult : results) {
					
					for (Result result : readAccessResult.getListOfResults()) {
						if(result.getPropertyIdentifier().equals(PropertyIdentifier.objectName)) {
							channelAddress = result.getReadResult().toString();
						} else if(result.getPropertyIdentifier().equals(PropertyIdentifier.description)) {
							description = result.getReadResult().toString();
						}
					}
				}
		        
		        ChannelScanInfo info = acceptedTypes.get(objectIdentifier.getObjectType()).getChannelScanInfo(channelAddress, description);
		        channelScanInfos.add(info);
		        objectHandles.put(channelAddress, objectIdentifier);
			}
		
		} catch (BACnetException e) {
			// distinguish between scan exception and connection exception
			if(testConnection()) {
				throw new ScanException("Error while scanning: " + e.getMessage(), e);
			} else {
				throw new ConnectionException("Remote device " + REMOTE_DEVICE.getInstanceNumber() + " is not reachable");
			}
		}
		
		return channelScanInfos;
	}

	@Override
	public Object read(List<ChannelRecordContainer> containers, Object containerListHandle, String samplingGroup)
			throws UnsupportedOperationException, ConnectionException {
				
		PropertyReferences references;
		
		if(containerListHandle == null || !(containerListHandle instanceof PropertyReferences)) {
			references = new PropertyReferences();
			for (ChannelRecordContainer container : containers) {
				ObjectIdentifier identifier = getObjectIdentifier(container);
				references.add(identifier, PropertyIdentifier.presentValue);
			}
		} else {
			references = (PropertyReferences) containerListHandle;
		}
		
		try {
		
			PropertyValues values = RequestUtils.readProperties(LOCAL_DEVICE, REMOTE_DEVICE, references, null);
			long timestamp = System.currentTimeMillis();
			
			for (ChannelRecordContainer container : containers) {
				ObjectIdentifier identifier = getObjectIdentifier(container);
				
				if(identifier != null) {
					try {
						Value value = convertValue(values.get(identifier, PropertyIdentifier.presentValue), identifier.getObjectType());
						container.setRecord(new Record(value, timestamp, Flag.VALID));
					} catch (PropertyValueException e) {
						container.setRecord(new Record(Flag.DRIVER_ERROR_READ_FAILURE));
					}
				} else {
					container.setRecord(new Record(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND));
				}
			}
			
		} catch (BACnetException e) {
			throw new ConnectionException(e.getMessage());
		}
    	
		return references;
	}

	private ObjectIdentifier getObjectIdentifier(ChannelRecordContainer container) throws UnsupportedOperationException, ConnectionException {
		
		if(container.getChannelHandle()!=null && container.getChannelHandle() instanceof ObjectIdentifier) {
			return (ObjectIdentifier) container.getChannelHandle();
		}
		
		if(objectHandles == null) {
			// scan for channels to get channel handles
			try {
				scanForChannels(null);
			} catch (ArgumentSyntaxException e) {
				throw new ConnectionException(e);
			} catch (ScanException e) {
				throw new ConnectionException(e);
			}
		}
		
		ObjectIdentifier objectIdentifier = objectHandles.get(container.getChannelAddress());
		container.setChannelHandle(objectIdentifier);
		
		return objectIdentifier;
	}


	@Override
	public void startListening(List<ChannelRecordContainer> containers, RecordsReceivedListener listener)
			throws UnsupportedOperationException, ConnectionException {
		
		if(containers == null) return;
		
        UnsignedInteger lifetime = new UnsignedInteger(0);
        com.serotonin.bacnet4j.type.primitive.Boolean issueConfirmedNotifications = new com.serotonin.bacnet4j.type.primitive.Boolean(true);
		
        // according to the OpenMUC specification, the new subscription list replaces the old one
        removeSubscriptions();
        
		for (ChannelRecordContainer channelRecordContainer : containers) {
			
			ObjectIdentifier objectIdentifier = null;
			
			if(channelRecordContainer.getChannelHandle() != null) {
				objectIdentifier = (ObjectIdentifier) channelRecordContainer.getChannelHandle();
			} else {
				if(objectHandles == null) {
					// scan for channels to get channel handles
					try {
						scanForChannels(null);
					} catch (ArgumentSyntaxException e) {
						throw new ConnectionException(e);
					} catch (ScanException e) {
						throw new ConnectionException(e);
					}
				}
				objectIdentifier = objectHandles.get(channelRecordContainer.getChannelAddress());
				channelRecordContainer.setChannelHandle(objectIdentifier);
			}
			
			if(!covContainers.containsKey(objectIdentifier)) {
				SubscribeCOVRequest request = new SubscribeCOVRequest(subscriberProcessIdentifier, objectIdentifier, issueConfirmedNotifications, lifetime);
				LOCAL_DEVICE.send(REMOTE_DEVICE, request);
				covContainers.put(objectIdentifier, channelRecordContainer);
			}
			
		} // foreach
		
		recordsReceivedListener = listener;
		
	}

	@Override
	public Object write(List<ChannelValueContainer> containers, Object containerListHandle) 
			throws UnsupportedOperationException, ConnectionException {
		
		// TODO add multiple write
		
		for (ChannelValueContainer channelValueContainer : containers) {
			
			ObjectIdentifier objectIdentifier = null;
			
			if(channelValueContainer.getChannelHandle() != null) {
				objectIdentifier = (ObjectIdentifier) channelValueContainer.getChannelHandle();
			} else {
				if(objectHandles == null) {
					// scan for channels to get channel handles
					try {
						scanForChannels(null);
					} catch (ArgumentSyntaxException e) {
						throw new ConnectionException(e);
					} catch (ScanException e) {
						throw new ConnectionException(e);
					}
				}
				objectIdentifier = objectHandles.get(channelValueContainer.getChannelAddress());
				channelValueContainer.setChannelHandle(objectIdentifier);
			}
			
			if(objectIdentifier != null) {
				
				Encodable value = convertValue(channelValueContainer.getValue(), objectIdentifier.getObjectType());
				
				if(value != null) {
					
					UnsignedInteger priority = (writePriority == null) ? null : new UnsignedInteger(writePriority.intValue());
					
					WritePropertyRequest request = new WritePropertyRequest(objectIdentifier, PropertyIdentifier.presentValue,
							null, value, priority);
					LOCAL_DEVICE.send(REMOTE_DEVICE, request);
					channelValueContainer.setFlag(Flag.VALID);
				} else {
					// tried to write a not supported object type
					channelValueContainer.setFlag(Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE);
				}	
			}
		}
		
		return null; // according to method documentation
		
	}

	@Override
	public void disconnect() {
		removeSubscriptions();
		LOCAL_DEVICE.getEventHandler().removeListener(this);
	}
	
	// filters object types that can be read/written by the driver 
	private List<ObjectIdentifier> getAcceptedObjects(List<ObjectIdentifier> allIdentifiers) {
		
		List<ObjectIdentifier> acceptedIdentifiers = new ArrayList<ObjectIdentifier>();
		
		for (ObjectIdentifier objectIdentifier : allIdentifiers) {
			if(acceptedTypes.containsKey(objectIdentifier.getObjectType())) {
				acceptedIdentifiers.add(objectIdentifier);
			}
		}
		return acceptedIdentifiers;
	}
	
	/**
	 * Gets the actual write priority between 1 and 16 or <code>null</code>, if the write priority
	 * is <i>relinquish_default</i> (priority below 16).
	 * 
	 * @return the write priority or <code>null</code>
	 */
	public Integer getWritePriority() {
		return writePriority;
	}

	/**
	 * Sets the BACnet priority for writing a value. The priority must by a value between 1 and 16, where
	 * 1 is the highest priority. If the reference is <code>null</code>, the <i>relinquish_default</i> value will be
	 * set (priority below 16).
	 * 
	 * @param writePriority priority between 1 and 16 or <code>null</code>
	 */
	public void setWritePriority(Integer writePriority) {
		
		if(writePriority != null) {
			int value = writePriority.intValue();
			if(value<1||value>16) {
				throw new IllegalArgumentException("Priority value must be between 1 and 16 or null");
			}
		}
		
		this.writePriority = writePriority;
	}


	// converts a BACnet value to an OpenMUC value
	private Value convertValue(Encodable value, ObjectType objectType) {
		
		Value result = null;
		
		if(objectType.equals(ObjectType.analogInput)||objectType.equals(ObjectType.analogOutput)||objectType.equals(ObjectType.analogValue)) {
			// present value of BACnet analog object is of type REAL, which is float in OpenMUC
			Real realValue = (Real) value;
			result = new FloatValue(realValue.floatValue());
		} else if(objectType.equals(ObjectType.binaryInput)||objectType.equals(ObjectType.binaryOutput)||objectType.equals(ObjectType.binaryValue)) {
			BinaryPV booleanValue = (BinaryPV) value;
			result = (booleanValue.intValue()==0) ? new BooleanValue(false) : new BooleanValue(true);
		} else if(objectType.equals(ObjectType.multiStateInput)||objectType.equals(ObjectType.multiStateInput)||objectType.equals(ObjectType.multiStateInput)) {
			UnsignedInteger integerValue = (UnsignedInteger) value;
			result = new IntValue(integerValue.intValue());
		} else {
			// default behavior in case of not handled object type
			result = new StringValue(value.toString());
		}
		
		return result;
	}
	
	private Encodable convertValue(Value value, ObjectType objectType) {
		
		// value null means to release the command in the priority list
		if(value == null) return new Null();
		
		Encodable result = null;
		
		if(objectType.equals(ObjectType.analogValue)||objectType.equals(ObjectType.analogOutput)) {
			result = new Real(value.asFloat());
		} else if(objectType.equals(ObjectType.binaryValue)||objectType.equals(ObjectType.binaryOutput)) {
			result = new BinaryPV(value.asInt());
		} else if(objectType.equals(ObjectType.multiStateValue)||objectType.equals(ObjectType.multiStateOutput)) {
			result = new UnsignedInteger(value.asInt());
		}
		
		return result;
	}
	
	private void removeSubscriptions() {
		
		if(!covContainers.isEmpty()) {
			for (ObjectIdentifier object : covContainers.keySet()) {
				LOCAL_DEVICE.send(REMOTE_DEVICE, new SubscribeCOVRequest(subscriberProcessIdentifier, object, null, null));
				covContainers.remove(object);
			}
		}
	}


	@Override
	public void listenerException(Throwable e) { }

	@Override
	public void iAmReceived(RemoteDevice d) { }

	@Override
	public boolean allowPropertyWrite(Address from, BACnetObject obj, PropertyValue pv) {
		return false;
	}

	@Override
	public void propertyWritten(Address from, BACnetObject obj, PropertyValue pv) { }

	@Override
	public void iHaveReceived(RemoteDevice d, RemoteObject o) { }

	@Override
	public void covNotificationReceived(UnsignedInteger subscriberProcessIdentifier, RemoteDevice initiatingDevice,
			ObjectIdentifier monitoredObjectIdentifier, UnsignedInteger timeRemaining, SequenceOf<PropertyValue> listOfValues) {
		
		if(recordsReceivedListener != null) {
			
			ChannelRecordContainer container = covContainers.get(monitoredObjectIdentifier);
			
			if(container != null) {
				
				Record record = new Record(convertValue(listOfValues.get(1).getValue(), monitoredObjectIdentifier.getObjectType()), 
						new Long(System.currentTimeMillis()), Flag.VALID);

				container.setRecord(record);
				List<ChannelRecordContainer> containers = new ArrayList<ChannelRecordContainer>();
				containers.add(container);

				recordsReceivedListener.newRecords(containers);
			}
		}
		
	} // covNotificationReceived()

	@Override
	public void eventNotificationReceived(UnsignedInteger processIdentifier, RemoteDevice initiatingDevice,
			ObjectIdentifier eventObjectIdentifier, TimeStamp timeStamp, UnsignedInteger notificationClass, UnsignedInteger priority,
			EventType eventType, CharacterString messageText, NotifyType notifyType, com.serotonin.bacnet4j.type.primitive.Boolean ackRequired,
			EventState fromState, EventState toState, NotificationParameters eventValues) { }
 

	@Override
	public void textMessageReceived(RemoteDevice textMessageSourceDevice, Choice messageClass, MessagePriority messagePriority,
			CharacterString message) { }

	@Override
	public void privateTransferReceived(Address from, UnsignedInteger vendorId,
			UnsignedInteger serviceNumber, Sequence serviceParameters) { }

	@Override
	public void reinitializeDevice(Address from, ReinitializedStateOfDevice reinitializedStateOfDevice) { }

	@Override
	public void synchronizeTime(Address from, DateTime dateTime, boolean utc) { }
	
	/**
	 * Tests the remote device connection by sending a read request for the device status property.
	 * 
	 * @return true if the request is successful, false otherwise
	 */
	private boolean testConnection() {
		ConfirmedRequestService request = new ReadPropertyRequest(REMOTE_DEVICE.getObjectIdentifier(), PropertyIdentifier.systemStatus);
		LOCAL_DEVICE.send(REMOTE_DEVICE, request);
		return true;
	}
	
}
