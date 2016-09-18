package bacnet4j;

import java.util.HashMap;
import java.util.Map;

import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;

public class PropertyIdentifierString {
	
	public static void main(String[] args) {
		
		Map<String, PropertyIdentifier> values = new HashMap<>();
		
		int maxValue = 386;
		
		for(int value=0;value<=maxValue;value++) {
			
			PropertyIdentifier identifier = new PropertyIdentifier(value);
			
			if(!identifier.toString().toLowerCase().startsWith("unknown")) {
				values.put(identifier.toString(), identifier);
			}
		}
		
		System.out.println("Created dictionary with " + values.size() + " value(s)");
		
		
	}
}
