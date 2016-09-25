package org.openmucextensions.driver.bacnet;

import org.apache.commons.lang3.StringUtils;
import org.openmuc.framework.driver.spi.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;

/**
 * This abstract call provides basic functionality for BACnet server and remote connections.
 * 
 * @author Pichler
 *
 */
public abstract class BACnetConnection implements Connection {

	protected final static Logger logger = LoggerFactory.getLogger(BACnetDriver.class);
	protected String separator = "#";
	
	/**
	 * Gets the object address out of a channel address string, which is the first part of the string
	 * split using the separator char (default: #). If the string doesn't contain such a separator char,
	 * the whole string will be returned. If the channel address is <code>null</code> or the separator
	 * char appears more than once, <code>null</code> will be returned.
	 * 
	 * @param channelAddress the channel address string
	 * @return the object address or <code>null</code>
	 */
	protected String getObjectAddress(final String channelAddress) {
		
		if(channelAddress == null) return null;
		int matches = StringUtils.countMatches(channelAddress, separator);
		if(matches==0) return channelAddress;
		
		// more than one separator char in address is invalid
		if(matches>1) {
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
		
		if(channelAddress == null) return null;
		int matches = StringUtils.countMatches(channelAddress, separator);
		
		// if no property value specified use default one
		if(matches==0) return PropertyIdentifier.presentValue;
		
		// more than one separator char in address is invalid
		if(matches>1) {
			logger.error("Channel address {} contains more than 1 separator char ({})", channelAddress, separator);
			return null;
		}
		
		String[] tokens = channelAddress.trim().split(separator);
		if(tokens.length==2 && PropertyIdentifierFactory.isValidIdentifier(tokens[1])) {
			return PropertyIdentifierFactory.getPropertyIdentifier(tokens[1]);
		} else {
			logger.error("Invalid property identifier '{}' in channel address {}", tokens[1], channelAddress);
			return null;
		}
	}

}
