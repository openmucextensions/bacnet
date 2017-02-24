package org.openmucextensions.driver.bacnet;

import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.service.unconfirmed.TimeSynchronizationRequest;
import com.serotonin.bacnet4j.type.constructed.DateTime;

public class TimeSyncTask extends TimerTask {

	private final static Logger logger = LoggerFactory.getLogger(BACnetDriver.class);
	
	private final LocalDevice localDevice;
	
	public TimeSyncTask(final LocalDevice device) {
		this.localDevice = device;
	}
	
	/**
	 * Sends a time synchronization request broadcast with the current system time to all BACnet
	 * devices in the local broadcast domain.
	 */
	@Override
	public void run() {
		
		synchronized (localDevice) {
			DateTime time = new DateTime(System.currentTimeMillis());
			TimeSynchronizationRequest request = new TimeSynchronizationRequest(time);
			localDevice.sendGlobalBroadcast(request);
		}
		
		logger.info("sended BACnet time synchronization broadcast with local time");
	}
}
