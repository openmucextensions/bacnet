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

import java.util.HashMap;

public class Settings extends HashMap<String, String> {

	private static final long serialVersionUID = -1827619677454357491L;

	public Settings() {
		super();
	}
	
	public Settings(String settingsString) {
		super();
		parseSettingsString(settingsString);
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
