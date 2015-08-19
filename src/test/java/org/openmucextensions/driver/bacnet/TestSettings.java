package org.openmucextensions.driver.bacnet;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import org.junit.Before;
import org.junit.Test;
import org.openmucextensions.driver.bacnet.Settings;

public class TestSettings {

	private Settings instance = null;
	
	@Before
	public void setUp() {
		instance = new Settings();
	}
	
	@Test
	public void testNull() {
		instance.parseSettingsString(null);
		assertTrue(instance.isEmpty());
	}
	
	@Test
	public void testEmpty() {
		instance.parseSettingsString("");
		assertTrue(instance.isEmpty());
	}
	
	@Test
	public void testWhitespaces() {
		instance.parseSettingsString("  ");
		assertTrue(instance.isEmpty());
	}
	
	@Test
	public void testSingleProperty() {
		instance.parseSettingsString(" property = test ");
		assertThat(instance.size(), is(1));
		assertThat(instance.get("property"), is("test"));	
	}
	
	@Test
	public void testMultipleProperties() {
		instance.parseSettingsString(" property1=value; property2 = value2");
		assertThat(instance.size(), is(2));
		assertThat(instance.get("property1"), is("value"));
		assertThat(instance.get("property2"), is("value2"));
	}
	
	@Test
	public void testGetEmptySettingsString() {
		String settings = instance.toSettingsString();
		assertThat(settings, is(""));
	}
	
	@Test
	public void testToSettingsString() {
		instance.put("property1", "value1");
		instance.put("property2", "value2");
		
		Settings settings = new Settings(instance.toSettingsString());
		assertEquals(settings, instance);
	}

}
