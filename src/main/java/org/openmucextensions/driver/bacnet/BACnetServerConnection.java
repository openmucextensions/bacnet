package org.openmucextensions.driver.bacnet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
import org.openmuc.framework.data.ValueType;
import org.openmuc.framework.driver.spi.ChannelRecordContainer;
import org.openmuc.framework.driver.spi.ChannelValueContainer;
import org.openmuc.framework.driver.spi.Connection;
import org.openmuc.framework.driver.spi.ConnectionException;
import org.openmuc.framework.driver.spi.RecordsReceivedListener;
import org.openmucextensions.driver.bacnet.BACnetUtils.ObjectPropertyIdentification;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.exception.BACnetServiceException;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.obj.ObjectProperties;
import com.serotonin.bacnet4j.obj.PropertyTypeDefinition;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;

/**
 * OpenMUC {@link Connection} implementation for BACnet which serves BACnet objects.
 * 
 * @author daniel
 */
public class BACnetServerConnection extends BACnetConnection {
    // private final static Logger logger = LoggerFactory.getLogger(BACnetServerConnection.class);
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
            final BACnetChannelHandle channelHandle = getChannelHandle(channelRecordContainer.getChannelHandle(), channelRecordContainer.getChannelAddress());
            if (channelHandle == null) {
                channelRecordContainer.setRecord(new Record(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND));
                continue;
            }
            
            // TODO discussion about ChannelHandle concept:
            // in this case, each channel will be read one by one - BACnet multiple read will not be used
            
            channelRecordContainer.setChannelHandle(channelHandle);
            channelHandle.read(channelRecordContainer);
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
            final BACnetChannelHandle channelHandle = getChannelHandle(channelRecordContainer.getChannelHandle(), channelRecordContainer.getChannelAddress());
            if (channelHandle == null) {
                channelRecordContainer.setRecord(new Record(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND));
                continue;
            }
            channelRecordContainer.setChannelHandle(channelHandle);
            channelHandle.startListening(channelRecordContainer, listener);
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
        List<ChannelConfig> orderedChannels = 
                deviceConfig.getChannels().stream().sorted(new ConfigComparator()).collect(Collectors.toList());
        for (ChannelConfig channel : orderedChannels) {
            initChannel(channel);
        }
    }

    /**
     * Compare {@link ChannelConfig}s and order by name and then (if name equal) by {@link PropertyIdentifier}
     * (see {@link PropertyIdentifierComparator})
     */
    private static class ConfigComparator implements Comparator<ChannelConfig> {
        final static Comparator<PropertyIdentifier> propIdComparator = new PropertyIdentifierComparator();
        @Override
        public int compare(ChannelConfig o1, ChannelConfig o2) {
            final Comparator<ObjectPropertyIdentification> comparator = 
                    Comparator.nullsLast(
                    Comparator.comparing(ObjectPropertyIdentification::getObjectName)
                              .thenComparing(Comparator.comparing(ObjectPropertyIdentification::getProperty, propIdComparator)));
            final ObjectPropertyIdentification o1NameAndPropertyType = BACnetUtils.getNameAndPropertyType(o1.getChannelAddress());
            final ObjectPropertyIdentification o2NameAndPropertyType = BACnetUtils.getNameAndPropertyType(o2.getChannelAddress());
            return comparator.compare(o1NameAndPropertyType, o2NameAndPropertyType);
        }
    }

    /**
     * Comparator for {@link PropertyIdentifier}s which orders by the int-value of the {@link PropertyIdentifier}-enumeration, but
     * prioritizes the {@link PropertyIdentifier#presentValue} (which gets the "lowest score").
     */
    private static class PropertyIdentifierComparator implements Comparator<PropertyIdentifier> {
        @Override
        public int compare(PropertyIdentifier o1, PropertyIdentifier o2) {
            final int order1 = (PropertyIdentifier.presentValue.equals(o1)) ? -1 : o1.intValue();
            final int order2 = (PropertyIdentifier.presentValue.equals(o2)) ? -1 : o2.intValue();
            return Comparator.<Integer>naturalOrder().compare(Integer.valueOf(order1), Integer.valueOf(order2));
        }
    }

    private void initChannel(ChannelConfig config) throws ConnectionException {
        final String channelId = config.getId();
        final String channelAddress = config.getChannelAddress();
        final ObjectPropertyIdentification nameAndType = BACnetUtils.getNameAndPropertyType(channelAddress);
        if (nameAndType == null) {
            logger.error("invalid configuration: channelAddress not correct for channel with id {}", channelId);
            channelAddressesWithInvalidConfiguration.add(channelAddress);
            return;
        }
        if (nameAndType.getProperty().equals(PropertyIdentifier.presentValue))
            createBacnetObject(config);
        else {
            // check if there already exists a BACnet object for this configuration
            final BACnetObject bacnetObject = localDevice.getObject(nameAndType.getObjectName());
            if (bacnetObject == null) {
                logger.error("invalid configuration: missing configuration for presentValue prior to channel with id {}", channelId);
                channelAddressesWithInvalidConfiguration.add(channelAddress);
                return;
            }
            // configuration ok
            channelAddressesWithInvalidConfiguration.remove(channelAddress);
            logger.debug("additional channel for existing BACnet object {} (with instance number {}) for property {}", bacnetObject.getObjectName(), bacnetObject.getInstanceId(), nameAndType.getProperty());
            // nothing else to do here
            return;
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
        
        boolean createSuccess = createBacnetObject(objectType, objectName, engUnit, config);
        if (!createSuccess) {
            channelAddressesWithInvalidConfiguration.add(channelAddress);
        }
    }

    /**
     * Create a BACnet object and add it to the local device.
     * @param type
     * @param objectName
     * @param config 
     * @throws ConnectionException
     */
    private boolean createBacnetObject(ObjectType type, String objectName, EngineeringUnits unit, ChannelConfig config) throws ConnectionException {
        final int instanceNumber = localDevice.getNextInstanceObjectNumber(type);
        logger.debug("creating new local BACnet object {} with type {} (and instance number {})", objectName, type, instanceNumber);
        
        // TODO should we read the initial value from configuration?
        final String initialValue;
        final PropertyTypeDefinition presValTypeDef = ObjectProperties.getPropertyTypeDefinition(type, PropertyIdentifier.presentValue);
        final ValueType valueType = ConversionUtil.getValueTypeMapping(presValTypeDef.getClazz());
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
            object.writeProperty(PropertyIdentifier.description, new CharacterString(config.getDescription()));
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
            final BACnetChannelHandle channelHandle = getChannelHandle(channelValueContainer.getChannelHandle(), channelValueContainer.getChannelAddress());
            if (channelHandle == null) {
                channelValueContainer.setFlag(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND);
                continue;
            }
            channelValueContainer.setChannelHandle(channelHandle);
            channelHandle.write(channelValueContainer);
        }
        return null;
    }
    
    private BACnetChannelHandle getChannelHandle(Object origChannelHandle, String channelAddress) {
        if(origChannelHandle != null) {
            return (BACnetChannelHandle) origChannelHandle;
        } else {
            final ObjectPropertyIdentification nameAndPropertyType = BACnetUtils.getNameAndPropertyType(channelAddress);
            final BACnetObject bacnetObj = findLocalObjectByName(nameAndPropertyType.getObjectName());
            if (bacnetObj == null)
                return null;
            return new BACnetChannelHandle(bacnetObj, nameAndPropertyType.getProperty());
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
