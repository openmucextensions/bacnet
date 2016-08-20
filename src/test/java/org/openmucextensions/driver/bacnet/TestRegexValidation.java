package org.openmucextensions.driver.bacnet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


/*
 * Settings strings should be validated using regular expressions. Valid setting strings
 * contain key-value-pairs like key=value. Leading and trailing whitespace for both, key and value
 * will be ignored. Multiple properties (key-value-pairs) will be separated using semicolons. 
 */
public class TestRegexValidation {

	private final String regex = "^(?:\\s*((?:[^=;\\s]+\\s+)*[^=;\\s]+)\\s*=\\s*((?:[^=;\\s]+\\s+)*[^=;\\s]+)\\s*(?:;(?!\\s*$)|$))+$";
	
	private final String[] valid = {"key=value", "key=value;key=value"};
	
	private final String[] invalid = {"key", "key=;", "key=", ""};

	@Test
	public void testRegEx() {
		
		for (String string : valid) {
			assertTrue(string.matches(regex));
		}
		
		for (String string : invalid) {
			assertFalse(string.matches(regex));
		}
		
	}

}
