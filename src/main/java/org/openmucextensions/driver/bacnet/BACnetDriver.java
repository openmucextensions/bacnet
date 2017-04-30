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

import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.openmuc.framework.config.ArgumentSyntaxException;
import org.openmuc.framework.config.ConfigService;
import org.openmuc.framework.config.DeviceConfig;
import org.openmuc.framework.config.DeviceScanInfo;
import org.openmuc.framework.config.DriverConfig;
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
import com.serotonin.bacnet4j.exception.BACnetTimeoutException;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkUtils;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.util.DiscoveryUtils;

/**
 * BACnet/IP communication driver for OpenMUC based on bacnet4J.
 * 
 * @author Daniel Lechner, Mike Pichler
 */
public class BACnetDriver implements DriverService {

    private final static Logger logger = LoggerFactory.getLogger(BACnetDriver.class);

    private final static long defaultDiscoverySleepTime = 2000;
    private ConfigService configService;

    // key is the remote device instance number
    private final Map<Integer, RemoteDevice> remoteDevices = new ConcurrentHashMap<Integer, RemoteDevice>();

    private volatile boolean deviceScanInterrupted = false;
    private final Object lock = new Object();

    private final static DriverInfo driverInfo = new DriverInfo("bacnet", // id
            // description
            "BACnet/IP communication protocol driver",
            // device address
            "BACnet device instance number is used as device address, optional host IP address e.g.: <instance_number>[;<host_ip>]",
            // settings
            "See https://github.com/openmucextensions/bacnet/wiki/Connect-to-a-device#settings",
            // channel address
            "The technical designation is used as channel address",
            // device scan parameters
            "See https://github.com/openmucextensions/bacnet/wiki/Scan-for-devices#settings");

    /**
     * The activate method will be called from the OSGi framework on startup of the bundle.
     * 
     * @param context
     *            OSGi component context
     * @param properties
     *            OSGi config admin properties
     */
    protected void activate(ComponentContext context, Map<String, Object> properties) {
        logger.info("BACnet communcation driver activated");
    }

    /**
     * The deactivate method will be called from the OSGi framework on shutdown of the bundle.
     * 
     * @param context
     *            OSGi component context
     */
    protected void deactivate(ComponentContext context) {
        LocalDeviceFactory.getInstance().dismissAll();
        logger.info("BACnet communication driver deactivated, all local devices terminated");
    }

    protected void setConfigService(ConfigService cs) {
        this.configService = cs;
    }

    protected void unsetConfigService(ConfigService cs) {
        if (configService == cs)
            configService = null;
    }

    @Override
    public DriverInfo getInfo() {
        return driverInfo;
    }

    @Override
    public void scanForDevices(String settingsString, DriverDeviceScanListener listener)
            throws UnsupportedOperationException, ArgumentSyntaxException, ScanException, ScanInterruptedException {

        deviceScanInterrupted = false;

        if (!Settings.isValidSettingsString(settingsString))
            throw new ArgumentSyntaxException("Settings string is invalid: " + settingsString);
        Settings settings = new Settings(settingsString);

        // get discoverySleepTime
        long discoverySleepTime = getDiscoverySleepTime(settings);

        // get broadcastIp
        String broadcastIP = getBroadcastIP(settings);

        if (settings.containsKey(Settings.SETTING_SCAN_PORT)) {
            Integer scanPort = parsePort(settings.get(Settings.SETTING_SCAN_PORT));
            scanAtPort(broadcastIP, scanPort, listener, discoverySleepTime);
        }
        else if(System.getProperty("org.openmucextensions.driver.bacnet.port")!=null) {
        	Integer scanPort = parsePort(System.getProperty("org.openmucextensions.driver.bacnet.port"));
        	scanAtPort(broadcastIP, scanPort, listener, discoverySleepTime);
        }
        else {
            int progress = 0;
            for (int scanPort = 0xBAC0; scanPort <= 0xBACF; scanPort++) {
                scanAtPort(broadcastIP, scanPort, listener, discoverySleepTime);
                progress += 6; // add 6% progress per port
                if (listener != null)
                    listener.scanProgressUpdate(progress);
                if (deviceScanInterrupted)
                    return;
            }
        }
    }

    @Override
    public void interruptDeviceScan() throws UnsupportedOperationException {
        deviceScanInterrupted = true;
    }

    @Override
    public Connection connect(final String deviceAddress, final String settingsString)
            throws ArgumentSyntaxException, ConnectionException {

        logger.debug("Connecting to device {} with settings {}...", deviceAddress, settingsString);

        DeviceAddress d = parseDeviceAddress(deviceAddress);
        String hostIp = d.hostIp();
        Integer remoteInstance = d.remoteInstance();

        if (!Settings.isValidSettingsString(settingsString))
            throw new ArgumentSyntaxException("Settings string is invalid: " + settingsString);
        Settings settings = new Settings(settingsString);

        LocalDevice localDevice = null;
        boolean isServer;
        
        boolean timeSync = (settings.containsKey(Settings.SETTING_TIME_SYNC)) ? Boolean.parseBoolean(settings.get(Settings.SETTING_TIME_SYNC)) : Boolean.FALSE;
        
        try {
            String broadcastIP = settings.get(Settings.SETTING_BROADCAST_IP);
            String localBindAddress = settings.get(Settings.SETTING_LOCALBIND_ADDRESS);

            Integer devicePort = null;
            if (settings.containsKey(Settings.SETTING_DEVICE_PORT))
                devicePort = parsePort(settings.get(Settings.SETTING_DEVICE_PORT));
            else if (settings.containsKey(Settings.SETTING_LOCAL_PORT))
                devicePort = parsePort(settings.get(Settings.SETTING_LOCAL_PORT));

            Integer localDeviceInstanceNumber = (settings.containsKey(Settings.SETTING_LOCAL_DVC_INSTANCENUMBER))
                    ? parseDeviceAddress(settings.get(Settings.SETTING_LOCAL_DVC_INSTANCENUMBER)).remoteInstance() : null;
            isServer = (settings.containsKey(Settings.SETTING_ISSERVER)) ? Boolean.parseBoolean(settings.get(Settings.SETTING_ISSERVER))
                    : Boolean.FALSE;

            localDevice = LocalDeviceFactory.getInstance().obtainLocalDevice(broadcastIP, localBindAddress, devicePort,
                    localDeviceInstanceNumber, timeSync);
        } catch (Exception e) {
            throw new ConnectionException("error while getting/creating local device: " + e.getMessage());
        }
        
        if (isServer) {

            // get configuration for this driver from OpenMUC config service
            final DriverConfig driverConfig = configService.getConfig().getDriver(driverInfo.getId());

            // try to find the device-configuration by address and settings-string
            final Optional<DeviceConfig> deviceConfig = driverConfig.getDevices()
                    .stream()
                    .filter(dc -> deviceAddress.equals(dc.getDeviceAddress())
                            && settingsString.equals(dc.getSettings()))
                    .findAny();
            if (!deviceConfig.isPresent()) {
                throw new ConnectionException(String.format("cannot find deviceConfig for address {}", deviceAddress));
            }

            final BACnetServerConnection connection = new BACnetServerConnection(localDevice, deviceConfig.get());

            return connection;
        }
        else {
            if (!remoteDevices.containsKey(remoteInstance)) {
                // remote device not found in cached ones

                if (!hostIp.isEmpty()) {
                    // --> addRemoteDevice with hostIp
                    addRemoteDevice(remoteInstance, hostIp, settings, localDevice);
                }
                else {
                    try {
                        // --> re-scan for devices
                        final Settings scanSettings = new Settings();
                        scanSettings.put(Settings.SETTING_BROADCAST_IP, settings.get(Settings.SETTING_BROADCAST_IP));
                        scanSettings.put(Settings.SETTING_LOCALBIND_ADDRESS, settings.get(Settings.SETTING_LOCALBIND_ADDRESS));
                        scanSettings.put(Settings.SETTING_SCAN_PORT, settings.get(Settings.SETTING_DEVICE_PORT));
                        scanForDevices(scanSettings.toSettingsString(), null);
                    } catch (UnsupportedOperationException ignore) {
                        throw new AssertionError();
                    } catch (ScanException e) {
                        throw new ConnectionException(e.getMessage());
                    } catch (ScanInterruptedException ignore) {
                        // scan not started by framework, so don't propagate exception
                    }
                }
            }

            RemoteDevice remoteDevice = remoteDevices.get(remoteInstance);
            if (remoteDevice == null)
                throw new ConnectionException("could not find device " + deviceAddress);

            // test if device is reachable (according to OpenMUC method documentation)
            try {
                DiscoveryUtils.getExtendedDeviceInformation(localDevice, remoteDevice);
            } catch (BACnetException e) {
                throw new ConnectionException("Couldn't reach device " + deviceAddress, e);
            }

            BACnetRemoteConnection connection = new BACnetRemoteConnection(localDevice, remoteDevice);

            Integer writePriority = (settings.containsKey(Settings.SETTING_WRITE_PRIORITY))
                    ? parseWritePriority(settings.get(Settings.SETTING_WRITE_PRIORITY)) : null;
            connection.setWritePriority(writePriority);

            return connection;
        }
    }

    private void addRemoteDevice(Integer remoteInstance, String hostIp, Settings settings, LocalDevice localDevice)
            throws ArgumentSyntaxException, ConnectionException {

        int port = 0;
        if (settings.containsKey(Settings.SETTING_DEVICE_PORT))
            port = parsePort(settings.get(Settings.SETTING_DEVICE_PORT));
        else
            port = parsePort(settings.get(Settings.SETTING_REMOTE_PORT));

        Address address = IpNetworkUtils.toAddress(hostIp, port);

        RemoteDevice remoteDevice = null;
        try {
            remoteDevice = localDevice.findRemoteDevice(address, remoteInstance);
        } catch (BACnetTimeoutException e) {
            throw new ConnectionException("Failed to establish connection to remote device. Cause. Timeout.");
        } catch (BACnetException e) {
            throw new ConnectionException(e.getMessage());
        }

        remoteDevices.put(remoteDevice.getInstanceNumber(), remoteDevice);
    }

    private void scanAtPort(String broadcastIP, Integer scanPort, DriverDeviceScanListener listener,
            long discoverySleepTime) throws ScanException, ScanInterruptedException, ArgumentSyntaxException {
        if (scanPort == null)
            throw new IllegalArgumentException("scanPort must not be null");
        final LocalDevice localDevice;
        // check if createLocalDevice will create a new one
        try {
            localDevice = LocalDeviceFactory.getInstance().obtainLocalDevice(broadcastIP, null, scanPort, null);
        } catch (Exception e) {
            throw new ScanException("error while getting/creating local device for scan: " + e.getMessage());
        }

        try {
            localDevice.sendGlobalBroadcast(new WhoIsRequest());

            // Thread.sleep(discoverySleepTime);
            synchronized (lock) {
                lock.wait(discoverySleepTime);
            }

        } catch (InterruptedException ignore) {
            logger.warn("device scan has been interrupted while waiting for responses");
        }

        logger.debug("found {} remote device(s) from scan at port 0x{}", localDevice.getRemoteDevices().size(),
                Integer.toHexString(scanPort.intValue()));

        for (RemoteDevice device : localDevice.getRemoteDevices()) {

            InetAddress hostIp = null;
            try {
                hostIp = IpNetworkUtils.getInetAddress(device.getAddress().getMacAddress());
            } catch (IllegalArgumentException e) {
                /* remote device not identified by IP-address? */ }

            if (deviceScanInterrupted)
                throw new ScanInterruptedException();

            try {
                DiscoveryUtils.getExtendedDeviceInformation(localDevice, device);
            } catch (BACnetException e) {
                logger.warn("error while reading extended device information from device {};{}",
                        device.getInstanceNumber(), (hostIp == null) ? "no ip" : hostIp.getHostAddress());
            }
            remoteDevices.put(device.getInstanceNumber(), device);

            if (listener != null) {
                final Settings scanSettings = new Settings();
                if (broadcastIP != null)
                    scanSettings.put(Settings.SETTING_BROADCAST_IP, broadcastIP);
                String deviceAddress = Integer.toString(device.getInstanceNumber());
                if (hostIp != null) {
                    deviceAddress += ';' + hostIp.getHostAddress();
                }
                scanSettings.put(Settings.SETTING_DEVICE_PORT, scanPort.toString());
                listener.deviceFound(
                        new DeviceScanInfo(deviceAddress, scanSettings.toSettingsString(), device.getName()));
            }
        }

        if ((localDevice.getRemoteDevices().size() == 0)) {
            logger.debug("dismiss local device {} because no remote devices have been found",
                    localDevice.getConfiguration().getInstanceId());
            LocalDeviceFactory.getInstance().dismissLocalDevice(scanPort);
        }
    }

    private int parsePort(String port) throws ArgumentSyntaxException {
        try {
            return Integer.decode(port);
        } catch (NumberFormatException e) {
            throw new ArgumentSyntaxException("port value is not a number");
        }
    }

    private Integer parseWritePriority(String priority) throws ArgumentSyntaxException {
        try {
            Integer writePriority = Integer.decode(priority);
            if (writePriority.intValue() < 1 || writePriority.intValue() > 16) {
                throw new ArgumentSyntaxException("writePriority value must be between 1 and 16");
            }
            return writePriority;
        } catch (NumberFormatException e) {
            throw new ArgumentSyntaxException("writePriority value is not a number");
        }
    }

    private DeviceAddress parseDeviceAddress(String deviceAddress) throws ArgumentSyntaxException {
        Integer remoteInstance;
        String hostIp = "";

        String[] addressArray = deviceAddress.split(";");

        try {
            remoteInstance = Integer.parseInt(addressArray[0]);
        } catch (NumberFormatException e) {
            throw new ArgumentSyntaxException("First argument of deviceAddress is not a number.");
        }

        // according to the BTL device implementation guidelines, the device instance number
        // shall be configurable in the range of 0 to 4194302
        if (remoteInstance < 0 || remoteInstance > 4194302) {
            throw new ArgumentSyntaxException("First argument of deviceAddress must be in the range of 0 to 4194302");
        }

        if (addressArray.length == 2) {
            hostIp = addressArray[1];
        }

        return new DeviceAddress(hostIp, remoteInstance);
    }
    
    private long getDiscoverySleepTime(final Settings settings) {
    	
    	long discoverySleepTime = 0;
    	
    	if (settings.containsKey(Settings.SETTING_SCAN_DISCOVERYSLEEPTIME)) {
            final String discoverySleepTimeSetting = settings.get(Settings.SETTING_SCAN_DISCOVERYSLEEPTIME);
            try {
                discoverySleepTime = Long.parseLong(discoverySleepTimeSetting);
            } catch (NumberFormatException nfe) {
                logger.warn("invalid parameter discoverySleepTime {}, using default value {}", Settings.SETTING_SCAN_DISCOVERYSLEEPTIME, defaultDiscoverySleepTime);
                discoverySleepTime = defaultDiscoverySleepTime;
            }
        }
        else {
            discoverySleepTime = defaultDiscoverySleepTime;
        }
    	
    	return discoverySleepTime;
    }
    
    private String getBroadcastIP(final Settings settings) {
    	
    	String broadcastIP = null;
        
    	if (settings.containsKey(Settings.SETTING_BROADCAST_IP)) {
            broadcastIP = settings.get(Settings.SETTING_BROADCAST_IP);
        }
        else {
            broadcastIP = IpNetwork.DEFAULT_BROADCAST_IP;
        }
        
        return broadcastIP;
    }

    private class DeviceAddress {
        private final String hostIp;
        private final Integer remoteInstance;

        DeviceAddress(final String hostIp, final Integer remoteInstance) {
            this.hostIp = hostIp;
            this.remoteInstance = remoteInstance;
        }

        String hostIp() {
            return hostIp;
        }

        Integer remoteInstance() {
            return remoteInstance;
        }
    }
}
