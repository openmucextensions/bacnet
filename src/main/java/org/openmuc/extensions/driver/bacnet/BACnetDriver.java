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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.openmuc.framework.config.ArgumentSyntaxException;
import org.openmuc.framework.config.DeviceScanInfo;
import org.openmuc.framework.config.DriverInfo;
import org.openmuc.framework.config.ScanException;
import org.openmuc.framework.config.ScanInterruptedException;
import org.openmuc.framework.driver.spi.Connection;
import org.openmuc.framework.driver.spi.ConnectionException;
import org.openmuc.framework.driver.spi.DriverDeviceScanListener;
import org.openmuc.framework.driver.spi.DriverService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.util.RequestUtils;

/**
 * BACnet/IP communication driver for OpenMUC based on bacnet4J.
 * 
 * @author Mike Pichler
 *
 */
public class BACnetDriver implements DriverService {

	private final static Logger logger = LoggerFactory.getLogger(BACnetDriver.class);

	private long whoIsSleepTime = 2*1000;
	private int nextDeviceInstanceNumber = 10000;
	
	// key is the IP port number of the local device
	private final Map<Integer, LocalDevice> localDevices = new ConcurrentHashMap<Integer, LocalDevice>();
	
	// key is the remote device instance number
	private final Map<Integer, RemoteDevice> remoteDevices = new ConcurrentHashMap<Integer, RemoteDevice>();
	
	private boolean deviceScanInterrupted = false;
	
	private final static DriverInfo driverInfo = new DriverInfo("bacnet", // id
			// description
			"BACnet/IP communication protocol driver",
			// device address
			"BACnet device instance number is used as device address",
			// settings
			"No settings possible",
			// channel address
			"The technical designation is used as channel address",
			// device scan parameters
			"No settings possible");
	
	/**
	 * The activate method will be called from the OSGi framework on startup of the bundle.
	 * 
	 * @param context OSGi component context
	 * @param properties OSGi config admin properties
	 */
	protected void activate(ComponentContext context, Map<String, Object> properties) {			
		logger.info("BACnet communcation driver activated");
	}
	
	/**
	 * The deactivate method will be called from the OSGi framework on shutdown of the bundle.
	 * 
	 * @param context OSGi component context
	 */
	protected void deactivate(ComponentContext context) {
		
		for (LocalDevice device : localDevices.values()) {
			device.terminate();
		}
		
		logger.info("BACnet communication driver deactivated, all local devices terminated");
	}
		
	@Override
	public DriverInfo getInfo() {
		return driverInfo;
	}

	@Override
	public void scanForDevices(String settingsString, DriverDeviceScanListener listener)
			throws UnsupportedOperationException, ArgumentSyntaxException, ScanException, ScanInterruptedException {
		
		deviceScanInterrupted = false;
		
		Settings settings = new Settings(settingsString);
		
		if(!settings.containsKey("port")) {
			int progress = 0;
			for(int port=0xBAC0; port<=0xBACF; port++) {
				settings.put("port", String.valueOf(port));
				scanAtPort(settings, listener);
				progress += 6; // add 6% progress per port
				if(listener!=null) listener.scanProgressUpdate(progress);
				if(deviceScanInterrupted) return;
			}
		} else {
			scanAtPort(settings, listener);
		}
	
	}

	@Override
	public void interruptDeviceScan() throws UnsupportedOperationException {
		deviceScanInterrupted = true;
	}

	@Override
	public Connection connect(String deviceAddress, String settingsString)
			throws ArgumentSyntaxException, ConnectionException {
		
		Integer remoteInstance = parseDeviceAddress(deviceAddress);
		
		Settings settings = new Settings(settingsString);
		Integer localDevicePort = (settings.containsKey("port")) ? parsePort(settings.get("port")) : IpNetwork.DEFAULT_PORT;
		
		LocalDevice localDevice = null;
		if(localDevices.containsKey(localDevicePort)) {
			localDevice = localDevices.get(localDevicePort);
		} else {
			try {
				localDevice = createLocalDevice(settings);
				logger.debug("created local BACnet device with instance number {} and port 0x{}",
						localDevice.getConfiguration().getInstanceId(), Integer.toHexString(localDevicePort.intValue()));
				localDevices.put(localDevicePort, localDevice);
			} catch (Exception e) {
				throw new ConnectionException("error while creating local device: " + e.getMessage());
			}
		}
		
		if(!remoteDevices.containsKey(remoteInstance)) {
			try {
				scanForDevices("", null);
			} catch (UnsupportedOperationException ignore) {
				throw new AssertionError();
			} catch (ScanException e) {
				throw new ConnectionException(e.getMessage());
			} catch (ScanInterruptedException ignore) {
				// scan not started by framework, so don't propagate exception
			}
		}
		
		RemoteDevice remoteDevice = remoteDevices.get(remoteInstance);
		if(remoteDevice == null) throw new ConnectionException("could not find device " + deviceAddress);
		
		// test if device is reachable (according to OpenMUC method documentation)
		try {
			RequestUtils.getExtendedDeviceInformation(localDevice, remoteDevice);
		} catch (BACnetException e) {
			throw new ConnectionException("Couldn't reach device " + deviceAddress, e);
		}
		
		BACnetConnection connection = new BACnetConnection(localDevice, remoteDevice);
		
		Integer writePriority = (settings.containsKey("writePriority")) ? parseWritePriority(settings.get("writePriority")) : null;
		connection.setWritePriority(writePriority);
		
		return connection;
	}
	
	private void scanAtPort(Settings settings, DriverDeviceScanListener listener) throws ScanException, ScanInterruptedException, ArgumentSyntaxException {
		
		Integer localDevicePort = (settings.containsKey("port")) ? parsePort(settings.get("port")) : IpNetwork.DEFAULT_PORT;
		
		LocalDevice localDevice = null;
		if(localDevices.containsKey(localDevicePort)) {
			localDevice = localDevices.get(localDevicePort);
		} else {
			try {
				localDevice = createLocalDevice(settings);
			} catch (Exception e) {
				throw new ScanException("error while creating local device: " + e.getMessage());
			}
		}
		
		try {
			localDevice.sendGlobalBroadcast(new WhoIsRequest());
			Thread.sleep(whoIsSleepTime);
		} catch (BACnetException e) {
			throw new ScanException("error while sending whois broadcast: " + e.getMessage());
		} catch (InterruptedException ignore) {
			logger.warn("device scan has been interrupted while waiting for responses");
		}
        
		logger.debug("found {} remote device(s) from scan at port 0x{}", localDevice.getRemoteDevices().size(), Integer.toHexString(localDevicePort.intValue())); 
		 
        for (RemoteDevice device : localDevice.getRemoteDevices()) {
        	
        	if(deviceScanInterrupted) throw new ScanInterruptedException();
        	
        	try {
				RequestUtils.getExtendedDeviceInformation(localDevice, device);
			} catch (BACnetException e) {
				logger.warn("error while reading extended device information from device " + device.getInstanceNumber());
			}
			remoteDevices.put(device.getInstanceNumber(), device);
			
			if(listener != null) {
				listener.deviceFound(new DeviceScanInfo(Integer.toString(device.getInstanceNumber()), settings.toSettingsString(), device.getName()));
			}
		}
        
        if(localDevice.getRemoteDevices().size()==0) {
        	if(!localDevices.containsKey(localDevicePort)) {
        		// logger.debug("local device {} will be terminated because no remote devices have been found", localDevice.getConfiguration().getInstanceId());
        		localDevice.terminate();
        	}
        } else {
        	localDevices.put(localDevicePort, localDevice);
        }
	}
	
	private int parsePort(String port) throws ArgumentSyntaxException {
		try {
			return Integer.decode(port).intValue();
		} catch (NumberFormatException e) {
			throw new ArgumentSyntaxException("port value is not a number");
		}
	}
	
	private Integer parseWritePriority(String priority) throws ArgumentSyntaxException {
		try {
			Integer writePriority = Integer.decode(priority);
			if(writePriority.intValue()<1 || writePriority.intValue()>16) {
				throw new ArgumentSyntaxException("writePriority value must be between 1 and 16");
			}
			return writePriority;
		} catch (NumberFormatException e) {
			throw new ArgumentSyntaxException("writePriority value is not a number");
		}
	}
	
	private Integer parseDeviceAddress(String deviceAddress) throws ArgumentSyntaxException {
		
		int result;
		
		try {
			result = Integer.parseInt(deviceAddress);
		} catch (NumberFormatException e) {
			throw new ArgumentSyntaxException("Argument deviceAddress is not a number");
		}
	
		// accoring to the BTL device implementation guidelines, the device instance number
		// shall be configurable in the range of 0 to 4194302
		if(result < 0 || result > 4194302)
			throw new ArgumentSyntaxException("Argument deviceAddress must be in the range of 0 to 4194302");
		
		return new Integer(result);
	}
	
	private LocalDevice createLocalDevice(Settings settings) throws ArgumentSyntaxException, Exception {
				
		String broadcastIP = (settings.containsKey("broadcastIP")) ? settings.get("broadcastIP") : IpNetwork.DEFAULT_BROADCAST_IP;
		String localBindAddress = (settings.containsKey("localBindAddress")) ? settings.get("localBindAddress") : IpNetwork.DEFAULT_BIND_IP;
		
		int port = (settings.containsKey("port")) ? parsePort(settings.get("port")) : IpNetwork.DEFAULT_PORT;
		int deviceInstanceNumber = (settings.containsKey("instanceNumber")) ? parseDeviceAddress(settings.get("instanceNumber")) : nextDeviceInstanceNumber++;
		
		IpNetwork network = new IpNetwork(broadcastIP, port, localBindAddress);
		Transport transport = new Transport(network);
		LocalDevice device = new LocalDevice(deviceInstanceNumber, transport);
		device.initialize();
		
		// logger.debug("created local BACnet device with instance number {} and port 0x{}", deviceInstanceNumber, Integer.toHexString(port));
		
		return device;
	}

}
