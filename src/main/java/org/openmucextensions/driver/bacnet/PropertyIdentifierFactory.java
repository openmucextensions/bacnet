package org.openmucextensions.driver.bacnet;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;

public class PropertyIdentifierFactory {
	
	private static Map<String, PropertyIdentifier> values = new HashMap<>();
	
	static {
		// create dictionary for all property identifier strings
		for(int value=0;value<387;value++) {
			
			PropertyIdentifier identifier = new PropertyIdentifier(value);
			
			if(!identifier.toString().toLowerCase().startsWith("unknown")) {
				values.put(identifier.toString(), identifier);
			}
		}
	}
	
	public static PropertyIdentifier getPropertyIdentifier(final String identifier) {
		if(values.containsKey(identifier)) return values.get(identifier);
		else return null;
	}
	
	public static Set<String> getIdentifierStrings() {
		return values.keySet();
	}
	
	public static boolean isValidIdentifier(final String identifier) {
		return values.containsKey(identifier);
	}
	
}
