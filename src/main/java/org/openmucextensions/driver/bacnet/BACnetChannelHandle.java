package org.openmucextensions.driver.bacnet;

import org.openmuc.framework.data.Flag;
import org.openmuc.framework.data.Record;
import org.openmuc.framework.data.Value;
import org.openmuc.framework.driver.spi.ChannelRecordContainer;
import org.openmuc.framework.driver.spi.ChannelValueContainer;
import org.openmuc.framework.driver.spi.RecordsReceivedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.exception.BACnetRuntimeException;
import com.serotonin.bacnet4j.exception.BACnetServiceException;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.obj.BACnetObjectListener;
import com.serotonin.bacnet4j.obj.ObjectProperties;
import com.serotonin.bacnet4j.obj.PropertyTypeDefinition;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;

/**
 * Channel handle for BACnet server objects.
 * @author daniel
 */
public class BACnetChannelHandle {
    private final static Logger logger = LoggerFactory.getLogger(BACnetChannelHandle.class);
    private final BACnetObject object;
    private final PropertyIdentifier property;
    private PropertyTypeDefinition propTypeDef;
    
    public BACnetChannelHandle(BACnetObject object, PropertyIdentifier property) {
        super();
        this.object = object;
        this.property = property;
    }
    
    public BACnetObject getObject() {
        return object;
    }
    public PropertyIdentifier getProperty() {
        return property;
    }
    
    private PropertyTypeDefinition getPropertyTypeDefinition() throws BACnetServiceException {
        if (propTypeDef == null) {
            final ObjectType objType = object.getProperty(PropertyIdentifier.objectType);
            propTypeDef = ObjectProperties.getPropertyTypeDefinition(objType, property);
        }
        return propTypeDef;
    }

    public void read(ChannelRecordContainer channelRecordContainer) {
        try {
            final Encodable bacnetValue = object.getProperty(property);
            final Value value = ConversionUtil.convertValue(bacnetValue, getPropertyTypeDefinition());
            channelRecordContainer.setRecord(new Record(value, System.currentTimeMillis(), Flag.VALID));
        }
        catch (BACnetServiceException e) {
            channelRecordContainer.setRecord(new Record(Flag.DRIVER_ERROR_READ_FAILURE));
            logger.debug("cannot read value of property {} of object {}. Message: {}", property, object.getObjectName(), e.getMessage());
        }
    }

    public void write(ChannelValueContainer channelValueContainer) {
        final Encodable value;
        try {
            try {
                value = ConversionUtil.convertValue(channelValueContainer.getValue(), getPropertyTypeDefinition());
            }
            catch (IllegalArgumentException iae) {
                // tried to write a not supported object type
                logger.error("cannot write value. " + iae.getMessage());
                channelValueContainer.setFlag(Flag.DRIVER_ERROR_CHANNEL_VALUE_TYPE_CONVERSION_EXCEPTION);
                return;
            }
            object.writeProperty(getPropertyTypeDefinition().getPropertyIdentifier(), value);
        }
        catch (BACnetRuntimeException | BACnetServiceException ex) {
            BACnetServiceException serviceException = (BACnetServiceException) ex.getCause();
            if (ErrorCode.writeAccessDenied.equals(serviceException.getErrorCode())) {
                channelValueContainer.setFlag(Flag.ACCESS_METHOD_NOT_SUPPORTED);
                return;
            }
            channelValueContainer.setFlag(Flag.UNKNOWN_ERROR);
        }
        channelValueContainer.setFlag(Flag.VALID);
    }

    public void startListening(ChannelRecordContainer channelRecordContainer, RecordsReceivedListener listener) {
        try {
            final PropertyTypeDefinition propTypeDef = getPropertyTypeDefinition();
            final BACnetObjectListener objectListener = new BACnetObjectChangeListener(listener, channelRecordContainer, propTypeDef);
            object.addListener(objectListener);
            // get initial value and notify listeners
            final Encodable initValue = object.getProperty(property);
            objectListener.propertyChange(property, null, initValue);
        }
        catch (BACnetServiceException e) {
            channelRecordContainer.setRecord(new Record(Flag.DRIVER_ERROR_READ_FAILURE));
            logger.debug("cannot read initial value of property {} of object {}. Message: {}", property, object.getObjectName(), e.getMessage());
        }
    }
}
