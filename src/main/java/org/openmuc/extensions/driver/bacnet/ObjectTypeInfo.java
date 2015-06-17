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
package org.openmuc.extensions.driver.bacnet;

import org.openmuc.framework.config.ChannelScanInfo;
import org.openmuc.framework.data.ValueType;

/**
 * This class stores object type information for converting BACnet objects to OpenMUC types.
 * 
 * @author Mike Pichler
 *
 */
public class ObjectTypeInfo {
	
	private final ValueType valueType;		// OpenMUC value type
	private final Integer valueTypeLength;	// OpenMUC value length (for strings or arrays, null otherwise)
	private final Boolean readable;			// is object readable
	private final Boolean writable;			// is object writeable
	
	public ObjectTypeInfo(ValueType valueType, Integer valueTypeLength, Boolean readable, Boolean writable) {
		this.valueType = valueType;
		this.valueTypeLength = valueTypeLength;
		this.readable = readable;
		this.writable = writable;
	}
	
	public ChannelScanInfo getChannelScanInfo(String address, String description) {	
		return new ChannelScanInfo(address, description, valueType, valueTypeLength, readable, writable);
	}
	
}
