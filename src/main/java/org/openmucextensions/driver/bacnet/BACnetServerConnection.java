package org.openmucextensions.driver.bacnet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.openmuc.framework.config.ArgumentSyntaxException;
import org.openmuc.framework.config.ChannelConfig;
import org.openmuc.framework.config.ChannelScanInfo;
import org.openmuc.framework.config.DeviceConfig;
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
import com.serotonin.bacnet4j.exception.BACnetRuntimeException;
import com.serotonin.bacnet4j.exception.BACnetServiceException;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.obj.BACnetObjectListener;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;

/**
 * OpenMUC {@link Connection} implementation for BACnet which serves BACnet objects.
 * 
 * @author daniel
 */
public class BACnetServerConnection implements Connection {
    private final static Logger logger = LoggerFactory.getLogger(BACnetServerConnection.class);
    // TODO: read from configuration
    private final static boolean SEND_IAM = true;

    private final LocalDevice localDevice;
    private final Collection<ObjectIdentifier> createdObjectIds = new ArrayList<>();
    /** channel addresses of channels with invalid configuration */
    private final Collection<String> channelAddressesWithInvalidConfiguration = new ArrayList<>();

    public BACnetServerConnection(LocalDevice localDevice, DeviceConfig deviceConfig) throws ConnectionException {
        this.localDevice = localDevice;
        Objects.requireNonNull(deviceConfig, "device configuration must not be null");
        logger.debug("deviceConfig set, using it to create BACnet objects...");
        initByDeviceConfig(deviceConfig);
        
        if (SEND_IAM)
            localDevice.sendGlobalBroadcast(localDevice.getIAm());
    }

    @Override
    public List<ChannelScanInfo> scanForChannels(String settings) throws UnsupportedOperationException, ArgumentSyntaxException, ScanException,
            ConnectionException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object read(List<ChannelRecordContainer> containers, Object containerListHandle, String samplingGroup) throws UnsupportedOperationException,
            ConnectionException {
        for (ChannelRecordContainer channelRecordContainer : containers) {
            // check if channel is misconfigured and set appropriate flag
            if (channelAddressesWithInvalidConfiguration.contains(channelRecordContainer.getChannel().getChannelAddress())) {
                final Record r = new Record(Flag.DRIVER_ERROR_CHANNEL_ADDRESS_SYNTAX_INVALID);
                channelRecordContainer.setRecord(r);
                continue;
            }
            final BACnetObject channelHandle = getChannelHandle(channelRecordContainer.getChannelHandle(), channelRecordContainer.getChannelAddress());
            if (channelHandle == null) {
                channelRecordContainer.setRecord(new Record(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND));
                continue;
            }
            channelRecordContainer.setChannelHandle(channelHandle);
            // get initial value and notify listeners
            try {
                final Value value = ConversionUtil.convertValue((Encodable)channelHandle.getProperty(PropertyIdentifier.presentValue), getObjectTypeOfBACnetObject(channelHandle));
                channelRecordContainer.setRecord(new Record(value, System.currentTimeMillis(), Flag.VALID));
                continue;
            }
            catch (BACnetServiceException e) {
                channelRecordContainer.setRecord(new Record(Flag.DRIVER_ERROR_READ_FAILURE));
                continue;
            }
        }
        return null;
    }

    @Override
    public void startListening(List<ChannelRecordContainer> containers, RecordsReceivedListener listener) throws UnsupportedOperationException,
            ConnectionException {
        if(containers == null) return;
        for (ChannelRecordContainer channelRecordContainer : containers) {
            // check if channel is misconfigured and set appropriate flag
            if (channelAddressesWithInvalidConfiguration.contains(channelRecordContainer.getChannel().getChannelAddress())) {
                final Record r = new Record(Flag.DRIVER_ERROR_CHANNEL_ADDRESS_SYNTAX_INVALID);
                channelRecordContainer.setRecord(r);
                continue;
            }
            final BACnetObject channelHandle = getChannelHandle(channelRecordContainer.getChannelHandle(), channelRecordContainer.getChannelAddress());
            if (channelHandle == null) {
                channelRecordContainer.setRecord(new Record(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND));
                continue;
            }
            channelRecordContainer.setChannelHandle(channelHandle);
            // create and register listener
            final ObjectType objectType = getObjectTypeOfBACnetObject(channelHandle);
            final BACnetObjectListener objectListener = new BACnetObjectChangeListener(listener, channelRecordContainer, objectType);
            channelHandle.addListener(objectListener);
            // get initial value and notify listeners
            final Encodable initValue;
            try {
                initValue = channelHandle.getProperty(PropertyIdentifier.presentValue);
            }
            catch (BACnetServiceException e) {
                throw new ConnectionException("cannot obtain initial value of internal BACnet object", e);
            }
            objectListener.propertyChange(PropertyIdentifier.presentValue, null, initValue);
        }
    }
    
    /**
     * Find the local BACnet object by it's object name.
     * @param objectName the name of the object which should be looked up
     * @return the found object or <code>null</code> if the object was not found.
     */
    private BACnetObject findLocalObjectByName(final String objectName) {
        return localDevice.getLocalObjects().stream()
                .filter(bo -> bo.getObjectName().equals(objectName))
                .findAny().orElse(null);
    }

    /**
     * Initialize the connection using an existing {@link DeviceConfig}. This method
     * will create all BACnet objects which are defined in the given configuration.
     * @param deviceConfig The configuration (must not be <code>null</code>).
     * @throws ConnectionException
     */
    private void initByDeviceConfig(DeviceConfig deviceConfig) throws ConnectionException {
        Objects.requireNonNull(deviceConfig, "deviceConfig must not be null");
        for (ChannelConfig channel : deviceConfig.getChannels()) {
            createBacnetObject(channel);
        }
    }

    /**
     * Create a BACnet object using the information of the {@link ChannelConfig} and add it to the local device.
     * @param config the channel configuration
     * @throws ConnectionException
     */
    private void createBacnetObject(ChannelConfig config) throws ConnectionException {
        final String objectName = config.getChannelAddress();
        final String channelId = config.getId();
        final String channelAddress = config.getChannelAddress();
        if (config.getUnit() == null) {
            logger.error("invalid configuration: unit of channel with id {} is not set", channelId);
            channelAddressesWithInvalidConfiguration.add(channelAddress);
            return;
        }
        final String[] configUnitParts = config.getUnit().split(";");
        if (configUnitParts.length != 2) {
            logger.error("invalid configuration: unit of channel with id {} does not have 2 parts. Expected syntax: \"BACnetObjectType;EngineeringUnit\"", channelId);
            channelAddressesWithInvalidConfiguration.add(channelAddress);
            return;
        }
        final String configObjectType = configUnitParts[0];
        final String configEngUnit = configUnitParts[1];
        
        final ObjectType objectType;
        try {
            objectType = BACnetUtils.getObjectTypeByString(configObjectType);
        }
        catch (NoSuchFieldException e) {
            logger.error("invalid configuration: object type of channel with id {} has invalid value {}. Expected one of {}", 
                    channelId, configObjectType, BACnetUtils.getAllObjectTypesAsString().stream().collect(Collectors.joining(",")));
            channelAddressesWithInvalidConfiguration.add(channelAddress);
            return;
        }
        catch (Exception e) {
            logger.error("cannot get object type of channel with id " + channelId + " which has valid (?) value " + configObjectType, e);
            channelAddressesWithInvalidConfiguration.add(channelAddress);
            return;
        }
        
        final EngineeringUnits engUnit;
        try {
            engUnit = BACnetUtils.getEngineeringUnitByString(configEngUnit);
        }
        catch (NoSuchFieldException e) {
            logger.error("invalid configuration: engineering unit of channel with id {} has invalid value {}. Expected one of {}", 
                    channelId, configEngUnit, BACnetUtils.getAllEngineeringUnitsAsString().stream().collect(Collectors.joining(",")));
            channelAddressesWithInvalidConfiguration.add(channelAddress);
            return;
        }
        catch (Exception e) {
            logger.error("cannot get engineering unit of channel with id " + channelId + " which has valid (?) value " + configEngUnit, e);
            channelAddressesWithInvalidConfiguration.add(channelAddress);
            return;
        }
        // configuration ok
        channelAddressesWithInvalidConfiguration.remove(channelAddress);
        
        boolean createSuccess = createBacnetObject(objectType, objectName, engUnit);
        if (!createSuccess) {
            channelAddressesWithInvalidConfiguration.add(channelAddress);
        }
    }

    /**
     * Create a BACnet object and add it to the local device.
     * @param type
     * @param objectName
     * @throws ConnectionException
     */
    private boolean createBacnetObject(ObjectType type, String objectName, EngineeringUnits unit) throws ConnectionException {
        int instanceNumber = localDevice.getNextInstanceObjectNumber(type);
        logger.debug("creating new local BACnet object {} with type {} (and instance number {})", objectName, type, instanceNumber);
        
        // TODO should we read the initial value from configuration?
        final String initialValue;
        final ValueType valueType = ConversionUtil.getValueTypeForObjectType(type);
        if (valueType == null) {
            logger.error("cannot create BACnet object for type {}", type);
            return false;
        }
        switch(valueType) {
        case BOOLEAN:
            initialValue = "false";
            break;
        case FLOAT:
            initialValue = "0.0";
            break;
        default:
            logger.error("cannot create BACnet object for type {}", type);
            return false;
        }
        
        try {
            final BACnetObject object = ConversionUtil.createBACnetObject(type, instanceNumber, objectName, initialValue, unit);
            localDevice.addObject(object);
            createdObjectIds.add(object.getId());
            logger.debug("local BACnet object {} created", objectName);
            return true;
        }
        catch (Exception e) { //BACnetServiceException or IllegalArgumentException
            logger.error("cannot create BACnet object: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public Object write(List<ChannelValueContainer> containers, Object containerListHandle) throws UnsupportedOperationException, ConnectionException {
        for (ChannelValueContainer channelValueContainer : containers) {
            // check if channel is misconfigured and set appropriate flag
            if (channelAddressesWithInvalidConfiguration.contains(channelValueContainer.getChannelAddress())) {
                channelValueContainer.setFlag(Flag.DRIVER_ERROR_CHANNEL_ADDRESS_SYNTAX_INVALID);
                continue;
            }
            final BACnetObject channelHandle = getChannelHandle(channelValueContainer.getChannelHandle(), channelValueContainer.getChannelAddress());
            if (channelHandle == null) {
                channelValueContainer.setFlag(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND);
                continue;
            }
            channelValueContainer.setChannelHandle(channelHandle);
            // convert and write value
            final ObjectType objectType = getObjectTypeOfBACnetObject(channelHandle);
            final Encodable value = ConversionUtil.convertValue(channelValueContainer.getValue(), objectType);
            if(value == null) {
                // tried to write a not supported object type
                logger.error("cannot write value of type " + objectType);
                channelValueContainer.setFlag(Flag.DRIVER_ERROR_CHANNEL_VALUE_TYPE_CONVERSION_EXCEPTION);
                return null;
            }
            try {
                channelHandle.writeProperty(PropertyIdentifier.presentValue, value);
            }
            catch (BACnetRuntimeException ex) {
                BACnetServiceException serviceException = (BACnetServiceException) ex.getCause();
                if (ErrorCode.writeAccessDenied.equals(serviceException.getErrorCode())) {
                    channelValueContainer.setFlag(Flag.ACCESS_METHOD_NOT_SUPPORTED);
                    return null;
                }
                channelValueContainer.setFlag(Flag.UNKNOWN_ERROR);
            }
            channelValueContainer.setFlag(Flag.VALID);
        }
        return null;
    }
    
    private BACnetObject getChannelHandle(Object origChannelHandle, String channelAddress) {
        if(origChannelHandle != null) {
            return (BACnetObject) origChannelHandle;
        } else {
            return findLocalObjectByName(channelAddress);
        }
    }

    @Override
    public void disconnect() {
        // remove all created objects
        for (final Iterator<ObjectIdentifier> it = createdObjectIds.iterator(); it.hasNext();) {
            final ObjectIdentifier nextObjectId = it.next();
            try {
                localDevice.removeObject(nextObjectId);
            }
            catch (BACnetServiceException e) {
                logger.warn("error during disconnect (removal of created local objects)", e);
            }
            it.remove();
        }
    }
    
    public static ObjectType getObjectTypeOfBACnetObject(BACnetObject obj) throws ConnectionException {
        try {
            return obj.getProperty(PropertyIdentifier.objectType);
        }
        catch (final BACnetServiceException e1) {
            throw new ConnectionException("cannot read object type of internal BACnet object", e1);
        }
    }
}
