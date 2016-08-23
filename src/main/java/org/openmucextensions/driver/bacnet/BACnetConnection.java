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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.openmuc.framework.config.ArgumentSyntaxException;
import org.openmuc.framework.config.ChannelScanInfo;
import org.openmuc.framework.config.ScanException;
import org.openmuc.framework.data.Flag;
import org.openmuc.framework.data.Record;
import org.openmuc.framework.data.Value;
import org.openmuc.framework.data.ValueType;
import org.openmuc.framework.driver.spi.ChannelRecordContainer;
import org.openmuc.framework.driver.spi.ChannelValueContainer;
import org.openmuc.framework.driver.spi.Connection;
import org.openmuc.framework.driver.spi.ConnectionException;
import org.openmuc.framework.driver.spi.RecordsReceivedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.RemoteObject;
import com.serotonin.bacnet4j.ServiceFuture;
import com.serotonin.bacnet4j.event.DeviceEventListener;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.PropertyValueException;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.obj.ObjectProperties;
import com.serotonin.bacnet4j.obj.PropertyTypeDefinition;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyMultipleAck;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedRequestService;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyMultipleRequest;
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
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.EventType;
import com.serotonin.bacnet4j.type.enumerated.MessagePriority;
import com.serotonin.bacnet4j.type.enumerated.NotifyType;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.notificationParameters.NotificationParameters;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.DiscoveryUtils;
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
	
	private final static Logger logger = LoggerFactory.getLogger(BACnetConnection.class);
	
	private final LocalDevice LOCAL_DEVICE;
	private final RemoteDevice REMOTE_DEVICE;
	
	private final UnsignedInteger subscriberProcessIdentifier = new UnsignedInteger(0);
	
	// BACnet write priority between 1 and 16 or null for relinquish_default
	private Integer writePriority = null;
	
	private final Map<ObjectType, ObjectTypeInfo> acceptedTypes;
	private Map<String, ObjectIdentifier> objectHandles = null;
	
	private Map<ObjectIdentifier, ChannelRecordContainer> covContainers = new ConcurrentHashMap<ObjectIdentifier, ChannelRecordContainer>();
	private RecordsReceivedListener recordsReceivedListener = null;
	/** List of objects with COV-subscription sent but no notification received */
	private List<ObjectIdentifier> openCOVNotifications = new ArrayList<>();
	
	/**
	 * Constructs a new <code>BACnetConnection</code> object for the specified remote device.
	 * 
	 * @param localDevice the local device instance to communicate with
	 * @param remoteDevice the remote device instance to communicate with
	 */
	public BACnetConnection(LocalDevice localDevice, RemoteDevice remoteDevice) {
		
		Objects.requireNonNull(localDevice, "local device instance must not be null");
		Objects.requireNonNull(remoteDevice, "remote device instance must not be null");
		
		LOCAL_DEVICE = localDevice;
		REMOTE_DEVICE = remoteDevice;
		
		 // accepted object types (only this object types will be provided as channel to the OpenMUC framework)
        // the related ObjectTypeInfo class specifies the OpenMUC behavior
        acceptedTypes = new HashMap<ObjectType, ObjectTypeInfo>();
        // analog objects are of type REAL in BACnet (32 bit, float)
        acceptedTypes.put(ObjectType.analogInput,
                new ObjectTypeInfo(ValueType.FLOAT, null, Boolean.TRUE, Boolean.FALSE, ObjectType.analogInput));
        acceptedTypes.put(ObjectType.analogOutput,
                new ObjectTypeInfo(ValueType.FLOAT, null, Boolean.TRUE, Boolean.TRUE, ObjectType.analogOutput));
        acceptedTypes.put(ObjectType.analogValue,
                new ObjectTypeInfo(ValueType.FLOAT, null, Boolean.TRUE, Boolean.TRUE, ObjectType.analogValue));
        acceptedTypes.put(ObjectType.binaryInput,
                new ObjectTypeInfo(ValueType.BOOLEAN, null, Boolean.TRUE, Boolean.FALSE, ObjectType.binaryInput));
        acceptedTypes.put(ObjectType.binaryOutput,
                new ObjectTypeInfo(ValueType.BOOLEAN, null, Boolean.TRUE, Boolean.TRUE, ObjectType.binaryOutput));
        acceptedTypes.put(ObjectType.binaryValue,
                new ObjectTypeInfo(ValueType.BOOLEAN, null, Boolean.TRUE, Boolean.TRUE, ObjectType.binaryValue));
        acceptedTypes.put(ObjectType.multiStateInput,
                new ObjectTypeInfo(ValueType.INTEGER, null, Boolean.TRUE, Boolean.FALSE, ObjectType.multiStateInput));
        acceptedTypes.put(ObjectType.multiStateOutput,
                new ObjectTypeInfo(ValueType.INTEGER, null, Boolean.TRUE, Boolean.TRUE, ObjectType.multiStateOutput));
        acceptedTypes.put(ObjectType.multiStateValue,
                new ObjectTypeInfo(ValueType.INTEGER, null, Boolean.TRUE, Boolean.TRUE, ObjectType.multiStateValue));
	
		LOCAL_DEVICE.getEventHandler().addListener(this);
	}

	@Override
	public List<ChannelScanInfo> scanForChannels(String settings)
			throws UnsupportedOperationException, ArgumentSyntaxException, ScanException, ConnectionException {
		
		logger.trace("starting scanForChannes with settings {} on remote device {}...", settings, REMOTE_DEVICE.getInstanceNumber());
		
		if(!testConnection()) throw new ConnectionException("Remote device " + REMOTE_DEVICE.getInstanceNumber() + " is not reachable");
		
		List<ChannelScanInfo> channelScanInfos = new ArrayList<ChannelScanInfo>();
		
		objectHandles = new HashMap<String, ObjectIdentifier>();
		
		try {
			
			// get object list from remote device
			@SuppressWarnings("unchecked")
			List<ObjectIdentifier> objectIdentifiers = ((SequenceOf<ObjectIdentifier>) RequestUtils.sendReadPropertyAllowNull(
					LOCAL_DEVICE, REMOTE_DEVICE, REMOTE_DEVICE.getObjectIdentifier(), PropertyIdentifier.objectList)).getValues();
		
			logger.trace("got basic information of {} objects. Filter by supported types...", objectIdentifiers.size());
			// filter object identifiers to just get accepted ones (see constructor)
			objectIdentifiers = getAcceptedObjects(objectIdentifiers);
			logger.trace("getting detailed information of {} channels...", objectIdentifiers.size());
			
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
			    logger.error("Error while scanning: " + e.getMessage(), e);
				throw new ScanException("Error while scanning: " + e.getMessage(), e);
			} else {
                logger.error("Remote device " + REMOTE_DEVICE.getInstanceNumber() + " is not reachable", e);
				throw new ConnectionException("Remote device " + REMOTE_DEVICE.getInstanceNumber() + " is not reachable");
			}
		}
		
        logger.trace("scanForChannels finished on remote device {}.", REMOTE_DEVICE.getInstanceNumber());
		return channelScanInfos;
	}

	@Override
	public Object read(List<ChannelRecordContainer> containers, Object containerListHandle, String samplingGroup)
			throws UnsupportedOperationException, ConnectionException {
		
	    if (logger.isTraceEnabled()) {
            final String channelAddresses = containers.stream().map(ChannelRecordContainer::getChannelAddress).collect(Collectors.joining(","));
            logger.trace("reading value for channels {}", channelAddresses);
	    }
	    
		final PropertyReferences references;
		
		if(containerListHandle == null || !(containerListHandle instanceof PropertyReferences)) {
			references = new PropertyReferences();
			for (ChannelRecordContainer container : containers) {
				final ObjectIdentifier identifier = getObjectIdentifier(container);
				if (identifier == null) {
				    // do not add PropertyReference with "null" identifier to references (since this causes an Exception in BACnet4j)
				    // logger.warn("identifier for " + container.getChannelAddress() + " is null. Probably unknown BACnet object name.");
				    continue;
				}
				references.add(identifier, PropertyIdentifier.presentValue);
			}
		} else {
			references = (PropertyReferences) containerListHandle;
		}
		
		try {
			PropertyValues values = RequestUtils.readProperties(LOCAL_DEVICE, REMOTE_DEVICE, references, null);
			long timestamp = System.currentTimeMillis();
			
			for (ChannelRecordContainer channelRecordContainer : containers) {
	            final ObjectIdentifier objectIdentifier = getObjectIdentifier(channelRecordContainer);
	            if (objectIdentifier == null) {
	                channelRecordContainer.setRecord(new Record(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND));
	                continue;
	            }
				
				try {
				    final Encodable propertyValue = values.get(objectIdentifier, PropertyIdentifier.presentValue);
				    if (logger.isTraceEnabled()) {
				        logger.trace("read new value for channel {} is type {} with value {}", 
				                channelRecordContainer.getChannel().getId(), 
				                propertyValue.getClass().getName(), 
				                propertyValue.toString());
				    }
				    final PropertyTypeDefinition propertyTypeDefinition = ObjectProperties.getPropertyTypeDefinition(objectIdentifier.getObjectType(), PropertyIdentifier.presentValue);
					final Value value = ConversionUtil.convertValue(propertyValue, propertyTypeDefinition);
					channelRecordContainer.setRecord(new Record(value, timestamp, Flag.VALID));
				} catch (PropertyValueException e) {
				    logger.warn(String.format("error while reading property of channel %s", channelRecordContainer.getChannel().getId()), e);
				    try {
				        logger.warn("BACnet read-error caused by {}", values.toString());
				    }
				    catch (Throwable t) {}
					channelRecordContainer.setRecord(new Record(Flag.DRIVER_ERROR_READ_FAILURE));
				}
			}
			
		} catch (BACnetException e) {
			throw new ConnectionException(e.getMessage());
		}
    	
		return references;
	}

    private ObjectIdentifier getObjectIdentifier(ChannelValueContainer container) throws UnsupportedOperationException, ConnectionException {
        final ObjectIdentifier objectIdentifier = getObjectIdentifier(container.getChannelHandle(), container.getChannelAddress());
        container.setChannelHandle(objectIdentifier);
        return objectIdentifier;
    }

    private ObjectIdentifier getObjectIdentifier(ChannelRecordContainer container) throws UnsupportedOperationException, ConnectionException {
        final ObjectIdentifier objectIdentifier = getObjectIdentifier(container.getChannelHandle(), container.getChannelAddress());
        container.setChannelHandle(objectIdentifier);
        return objectIdentifier;
    }
    
    private ObjectIdentifier getObjectIdentifier(Object origChannelHandle, String channelAddress) throws UnsupportedOperationException, ConnectionException {
		if(origChannelHandle != null && origChannelHandle instanceof ObjectIdentifier) {
			return (ObjectIdentifier) origChannelHandle;
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
		return objectHandles.get(channelAddress);
	}


	@Override
	public void startListening(List<ChannelRecordContainer> containers, RecordsReceivedListener listener)
			throws UnsupportedOperationException, ConnectionException {
		
		if(containers == null) return;
		
        if (logger.isTraceEnabled()) {
            final String channelAddresses = containers.stream().map(ChannelRecordContainer::getChannelAddress).collect(Collectors.joining(","));
            logger.trace("starting listening for channels {}", channelAddresses);
        }

        UnsignedInteger lifetime = new UnsignedInteger(0);
        com.serotonin.bacnet4j.type.primitive.Boolean issueConfirmedNotifications = new com.serotonin.bacnet4j.type.primitive.Boolean(true);
		
        // according to the OpenMUC specification, the new subscription list replaces the old one
        removeSubscriptions();
        
		for (ChannelRecordContainer channelRecordContainer : containers) {
			
            final ObjectIdentifier objectIdentifier = getObjectIdentifier(channelRecordContainer);
            if (objectIdentifier == null) {
                channelRecordContainer.setRecord(new Record(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND));
                continue;
            }
			
            synchronized (openCOVNotifications) {
                openCOVNotifications.add(objectIdentifier);
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
		
        if (logger.isTraceEnabled()) {
            final String channelAddresses = containers.stream().map(ChannelValueContainer::getChannelAddress).collect(Collectors.joining(","));
            logger.trace("writing value to channels {}", channelAddresses);
        }
	    
		// TODO add multiple write
		
		for (ChannelValueContainer channelValueContainer : containers) {
            final ObjectIdentifier objectIdentifier = getObjectIdentifier(channelValueContainer);
            if (objectIdentifier == null) {
                channelValueContainer.setFlag(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND);
                continue;
            }
			
			if(objectIdentifier != null) {
                final PropertyTypeDefinition propertyTypeDefinition = ObjectProperties.getPropertyTypeDefinition(objectIdentifier.getObjectType(), PropertyIdentifier.presentValue);
				Encodable value = ConversionUtil.convertValue(channelValueContainer.getValue(), propertyTypeDefinition);
				
				if(value != null) {
					UnsignedInteger priority = (writePriority == null) ? null : new UnsignedInteger(writePriority.intValue());
					
					WritePropertyRequest request = new WritePropertyRequest(objectIdentifier, PropertyIdentifier.presentValue,
							null, value, priority);
					LOCAL_DEVICE.send(REMOTE_DEVICE, request);
					channelValueContainer.setFlag(Flag.VALID);
				} else {
					// tried to write a not supported object type
					logger.debug("cannot write value of type " + objectIdentifier.getObjectType());
					channelValueContainer.setFlag(Flag.DRIVER_ERROR_CHANNEL_VALUE_TYPE_CONVERSION_EXCEPTION);
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

	private void removeSubscriptions() {
		
		if(!covContainers.isEmpty()) {
			for (ObjectIdentifier object : covContainers.keySet()) {
				LOCAL_DEVICE.send(REMOTE_DEVICE, new SubscribeCOVRequest(subscriberProcessIdentifier, object, null, null));
				covContainers.remove(object);
	            synchronized (openCOVNotifications) {
	                openCOVNotifications.remove(object);
	            }
			}
		}
	}


	@Override
	public void listenerException(Throwable e) { 
	    logger.trace(String.format("%s: received listenerException on connection to remote %s", LOCAL_DEVICE.getConfiguration().getId(), REMOTE_DEVICE.getName()),e);
	}

	@Override
	public void iAmReceived(RemoteDevice d) { 
        logger.trace(String.format("%s: received iAm from remote %s", LOCAL_DEVICE.getConfiguration().getId(), d.getName()));
	}

	@Override
	public boolean allowPropertyWrite(Address from, BACnetObject obj, PropertyValue pv) {
		return false;
	}

	@Override
	public void propertyWritten(Address from, BACnetObject obj, PropertyValue pv) { 
        logger.trace(String.format("%s: received propertyWritten from %s, to object %s, property %s", LOCAL_DEVICE.getConfiguration().getId(), 
                from.toString(), obj.getObjectName(), pv));
	}

	@Override
	public void iHaveReceived(RemoteDevice d, RemoteObject o) { 
        logger.trace(String.format("%s: received iHave from remote %s for object %s", LOCAL_DEVICE.getConfiguration().getId(), d.getName(), o.getObjectName()));
	}

	@Override
	public void covNotificationReceived(UnsignedInteger subscriberProcessIdentifier, RemoteDevice initiatingDevice,
			ObjectIdentifier monitoredObjectIdentifier, UnsignedInteger timeRemaining, SequenceOf<PropertyValue> listOfValues) {
		
	    if (!REMOTE_DEVICE.equals(initiatingDevice)) {
	        return;
	    }
	    
        synchronized (openCOVNotifications) {
            openCOVNotifications.remove(monitoredObjectIdentifier);
        }
	    
		if(recordsReceivedListener != null) {
			
			ChannelRecordContainer container = covContainers.get(monitoredObjectIdentifier);
			
			if(container == null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("received (listener) cov notification from {} for unknown ObjectIdentifier {} with values {}", 
                            initiatingDevice.getName(), monitoredObjectIdentifier,
                            listOfValues.getValues().stream()
                                .map(PropertyValue::getValue)
                                .map(v -> String.format("type: %s, value: %s", v.getClass().getName(), v))
                                .collect(Collectors.joining("; "))
                            );
                }
			}
			else {
                final PropertyValue newPropertyValue = listOfValues.get(1);
                if (logger.isTraceEnabled()) {
                    logger.trace("received (listener) new value for channel {} is type {} with value {}", 
                            container.getChannel().getId(), 
                            newPropertyValue.getValue().getClass().getName(), 
                            newPropertyValue.getValue().toString());
                }

                final PropertyTypeDefinition propertyTypeDefinition = ObjectProperties.getPropertyTypeDefinition(monitoredObjectIdentifier.getObjectType(), newPropertyValue.getPropertyIdentifier());
				Record record = new Record(ConversionUtil.convertValue(newPropertyValue.getValue(), propertyTypeDefinition), 
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
			EventState fromState, EventState toState, NotificationParameters eventValues) { 
        logger.trace(String.format("%s: received eventNotification from remote device %s with objectIdentifier %s, event-/notificationType %s/%s and message %s", LOCAL_DEVICE.getConfiguration().getId(), 
                initiatingDevice.getName(), eventObjectIdentifier, eventType, notifyType, messageText));
	}
 

	@Override
	public void textMessageReceived(RemoteDevice textMessageSourceDevice, Choice messageClass, MessagePriority messagePriority,
			CharacterString message) { 
        logger.trace(String.format("%s: received text-message from remote device %s: %s", LOCAL_DEVICE.getConfiguration().getId(), 
                textMessageSourceDevice.getName(), message));
	}

	@Override
	public void privateTransferReceived(Address from, UnsignedInteger vendorId,
			UnsignedInteger serviceNumber, Sequence serviceParameters) { }

	@Override
	public void reinitializeDevice(Address from, ReinitializedStateOfDevice reinitializedStateOfDevice) { 
        logger.trace(String.format("%s: reinitializeDevice request received from %s with state %s", LOCAL_DEVICE.getConfiguration().getId(), 
                from, reinitializedStateOfDevice));
	}

	@Override
	public void synchronizeTime(Address from, DateTime dateTime, boolean utc) { }
	
	/**
	 * Tests the remote device connection by sending a read request for the device status property.
	 * 
	 * @return true if the request is successful, false otherwise
	 */
	private boolean testConnection() {
		try {
			DiscoveryUtils.getExtendedDeviceInformation(LOCAL_DEVICE, REMOTE_DEVICE);
			return true;
		} catch (BACnetException e) {
			return false;
		}
	}
}
