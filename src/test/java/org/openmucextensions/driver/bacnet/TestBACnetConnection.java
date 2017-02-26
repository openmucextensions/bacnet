package org.openmucextensions.driver.bacnet;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.openmuc.framework.config.ArgumentSyntaxException;
import org.openmuc.framework.config.ChannelScanInfo;
import org.openmuc.framework.config.ScanException;
import org.openmuc.framework.driver.spi.ChannelRecordContainer;
import org.openmuc.framework.driver.spi.ChannelValueContainer;
import org.openmuc.framework.driver.spi.ConnectionException;
import org.openmuc.framework.driver.spi.RecordsReceivedListener;

import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;

public class TestBACnetConnection extends BACnetConnection {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testGetObjectAddress() {
		
		assertThat(getObjectAddress("object#property"), is("object"));
		assertThat(getObjectAddress("object"), is("object"));
		
		assertNull(getObjectAddress("object#object#property"));
		assertNull(getObjectAddress(null));

	}
	
	@Test
	public void testGetPropertyIdentifier() {
		
		// test some property identifiers
		assertThat(getPropertyIdentifier("object#presentValue"), is(PropertyIdentifier.presentValue));
		assertThat(getPropertyIdentifier("object#minPresValue"), is(PropertyIdentifier.minPresValue));
		assertThat(getPropertyIdentifier("object#lowLimit"), is(PropertyIdentifier.lowLimit));
		
	}
	
	@Test
	public void testObjectTypeEnum() {
		
		assertThat(ObjectType.analogInput.intValue(), is(0));
		assertThat(ObjectType.binaryValue.intValue(), is(5));
		
	}

	// **** methods of abstract class ****
	
	@Override
	public List<ChannelScanInfo> scanForChannels(String settings)
			throws UnsupportedOperationException, ArgumentSyntaxException, ScanException, ConnectionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object read(List<ChannelRecordContainer> containers, Object containerListHandle, String samplingGroup)
			throws UnsupportedOperationException, ConnectionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void startListening(List<ChannelRecordContainer> containers, RecordsReceivedListener listener)
			throws UnsupportedOperationException, ConnectionException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object write(List<ChannelValueContainer> containers, Object containerListHandle)
			throws UnsupportedOperationException, ConnectionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void disconnect() {
		// TODO Auto-generated method stub
		
	}
}
