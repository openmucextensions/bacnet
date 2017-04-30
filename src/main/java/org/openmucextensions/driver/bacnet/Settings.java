/*  OpenMUC Extensions BACnet Driver
 *  Copyright (C) 2014-2017
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

import java.util.HashMap;

public class Settings extends HashMap<String, String> {

	private static final long serialVersionUID = -1827619677454357491L;

	public static final String VALID_SETTINGS_STRING_REGEX = "^(?:\\s*((?:[^=;\\s]+\\s+)*[^=;\\s]+)\\s*=\\s*((?:[^=;\\s]+\\s+)*[^=;\\s]+)\\s*(?:;(?!\\s*$)|$))+$";
	
    /** Setting-name for the sleep time of the discovery process (in ms) */
    public final static String SETTING_SCAN_DISCOVERYSLEEPTIME = "discoverySleepTime";
    /** Setting-name for the single port used for scanning */
    public final static String SETTING_SCAN_PORT = "scanPort";
    /** Setting-name for the broadcast ip address */
    public final static String SETTING_BROADCAST_IP = "broadcastIP";
    /** Setting-name for the local ip address used for binding of the driver */
    public final static String SETTING_LOCALBIND_ADDRESS = "localBindAddress";
    /** Setting-name for device port */
    public final static String SETTING_DEVICE_PORT = "devicePort";
    /** Setting-name for the instance number of the local device (for local BACnet server) */
    public final static String SETTING_LOCAL_DVC_INSTANCENUMBER = "localInstanceNumber";
    /** Setting-name for the flag whether this device is a BACnet server */
    public final static String SETTING_ISSERVER = "isServer";
    /** Setting-name for BACnet write priority */
    public final static String SETTING_WRITE_PRIORITY = "writePriority";
    /** Setting-name for time synchronization request flag */
    public final static String SETTING_TIME_SYNC = "timeSync";

    /** Setting-name for the local UDP port which has to be used (for local BACnet server) */
    @Deprecated
    public final static String SETTING_LOCAL_PORT = "localDevicePort";
    /** Setting-name for the UDP port of the remote device */
    @Deprecated
    public final static String SETTING_REMOTE_PORT = "remoteDevicePort";
	
	public Settings() {
		super();
	}
	
	public Settings(String settingsString) {
		
		super();
		
		if(!isValidSettingsString(settingsString)) {
			throw new IllegalArgumentException("Settings string is not valid");
		}
		
		parseSettingsString(settingsString);
	}
	
	/**
	 * Returns true, if the specified settings string is valid, empty or <code>null</code>.
	 * 
	 * @param settings settings string to validate
	 * @return true, if the specified settings string is valid, empty or <code>null</code>
	 */
	public static boolean isValidSettingsString(final String settings) {
		if(settings == null) return true;
		if(settings.isEmpty()) return true;
		return settings.matches(VALID_SETTINGS_STRING_REGEX);
	}
	
	public void parseSettingsString(String settings) {
		
		if(settings == null) {
			clear();
			return;
		}
		
		if(settings.trim().equals("")) {
			clear();
			return;
		}
		
		String[] properties = settings.split(";");
		
		for (String property : properties) {
			property = property.trim();
			String[] tokens = property.split("=");
			if(tokens.length == 2) {
				put(tokens[0].trim(), tokens[1].trim());
			} else {
				throw new IllegalArgumentException("Invalid property " + property);
			}
		}
	}
	
	public String toSettingsString() {
		
		StringBuilder builder = new StringBuilder();
		for (String key : keySet()) {
			if(builder.length()>0) builder.append(";");
			builder.append(key + "=" + get(key));
		}
		
		return builder.toString();
	}
	
}
