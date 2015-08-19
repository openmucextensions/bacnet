package org.openmucextensions.driver.bacnet;

import org.openmuc.framework.data.Record;
import org.openmuc.framework.dataaccess.Channel;
import org.openmuc.framework.driver.spi.ChannelRecordContainer;

/**
 * Implementation of the <code>ChannelRecordContainer</code> interface for testing purposes.
 * 
 * @author Mike Pichler
 *
 */
public class ChannelRecordContainerImpl implements ChannelRecordContainer {

	private Record record = null;
	private String channelAddress = null;
	private Object channelHandle = null;
	
	public ChannelRecordContainerImpl(String channelAddress) {
		this.channelAddress = channelAddress;
	}
	
	@Override
	public Record getRecord() {
		return record;
	}

	@Override
	public Channel getChannel() {
		return null;
	}

	@Override
	public String getChannelAddress() {
		return channelAddress;
	}

	@Override
	public Object getChannelHandle() {
		return channelHandle;
	}

	@Override
	public void setChannelHandle(Object handle) {
		this.channelHandle = handle;
	}

	@Override
	public void setRecord(Record record) {
		this.record = record;
	}

	@Override
	public ChannelRecordContainer copy() {
		return null;
	}
	
}
