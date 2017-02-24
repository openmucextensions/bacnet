package org.openmucextensions.driver.bacnet;

import java.util.Timer;

import org.apache.commons.lang3.StringUtils;
import org.openmuc.framework.driver.spi.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.util.PropertyReferences;

/**
 * This abstract call provides basic functionality for BACnet server and remote connections.
 * 
 * @author Pichler
 *
 */
public abstract class BACnetConnection implements Connection {

    protected final static Logger logger = LoggerFactory.getLogger(BACnetDriver.class);
    protected String separator = "#";

    private Timer timeSyncTimer = null;

    /**
     * Gets the object address out of a channel address string, which is the first part of the string split using the
     * separator char (default: #). If the string doesn't contain such a separator char, the whole string will be
     * returned. If the channel address is <code>null</code> or the separator char appears more than once,
     * <code>null</code> will be returned.
     * 
     * @param channelAddress
     *            the channel address string
     * @return the object address or <code>null</code>
     */
    protected String getObjectAddress(final String channelAddress) {

        if (channelAddress == null)
            return null;
        int matches = StringUtils.countMatches(channelAddress, separator);
        if (matches == 0)
            return channelAddress;

        // more than one separator char in address is invalid
        if (matches > 1) {
            logger.error("Channel address {} contains more than 1 separator char ({})", channelAddress, separator);
            return null;
        }

        String[] tokens = channelAddress.trim().split(separator);
        return tokens[0];

    }

    /**
     * Gets the property identifier of the channel address, separated by the specified char in attribute separator.
     * 
     * @param channelAddress
     * @return the property identifier object or <code>null</code> in case of errors
     */
    protected PropertyIdentifier getPropertyIdentifier(final String channelAddress) {

        if (channelAddress == null)
            return null;
        int matches = StringUtils.countMatches(channelAddress, separator);

        // if no property value specified use default one
        if (matches == 0)
            return PropertyIdentifier.presentValue;

        // more than one separator char in address is invalid
        if (matches > 1) {
            logger.error("Channel address {} contains more than 1 separator char ({})", channelAddress, separator);
            return null;
        }

        String[] tokens = channelAddress.trim().split(separator);
        if (tokens.length == 2 && PropertyIdentifierFactory.isValidIdentifier(tokens[1])) {
            return PropertyIdentifierFactory.getPropertyIdentifier(tokens[1]);
        }
        else {
            logger.error("Invalid property identifier '{}' in channel address {}", tokens[1], channelAddress);
            return null;
        }
    }

    /**
     * Adds the property identifiers that are relevant for a parameter list to a <code>PropertyReferences</code> object.
     * 
     * @param references
     *            the <code>PropertyReferences</code> object
     * @param objectId
     *            the object identifier
     */
    protected void addPropertyIdentifiers(final PropertyReferences references, final ObjectIdentifier objectId) {
        references.add(objectId, PropertyIdentifier.units);
        references.add(objectId, PropertyIdentifier.description);
        switch (objectId.getObjectType().intValue()) {

        case 0: // analog input
        case 13: // multistate input
            references.add(objectId, PropertyIdentifier.lowLimit);
            references.add(objectId, PropertyIdentifier.highLimit);
            references.add(objectId, PropertyIdentifier.limitEnable);
            break;

        case 3: // binary input
            break;

        case 1: // analog output
        case 4: // binary output
        case 14: // multistate output
            break;

        case 2: // analog value
        case 5: // binary value
        case 19: // multistate value
            references.add(objectId, PropertyIdentifier.presentValue);
            break;

        case 6: // calendar
            references.add(objectId, PropertyIdentifier.dateList);
            break;

        case 17: // schedule
            references.add(objectId, PropertyIdentifier.weeklySchedule);
            references.add(objectId, PropertyIdentifier.exceptionSchedule);
            break;

        default: // unknown or not relevant object

        }

    }
    
    void startTimeSynchronizationTimer(final LocalDevice localDevice) {
    	
    	if(timeSyncTimer!=null) timeSyncTimer.cancel();
    	
    	long delay = 5*1000; // first execution delay
    	long interval = 24*60*60*1000; // execution interval
    	
    	timeSyncTimer = new Timer("BACnet Time Synchronization Timer", true);
        timeSyncTimer.scheduleAtFixedRate(new TimeSyncTask(localDevice), delay, interval);
        
        logger.debug("BACnet time synchronization timer started");
    }
    
    void stopTimeSynchronizationTimer() {
    	
    	if(timeSyncTimer!=null) timeSyncTimer.cancel();
    	timeSyncTimer = null;
    	
    	logger.debug("BACnet time synchronization timer stopped");
    }
    
    public abstract void startTimeSynchronization();
    
}
