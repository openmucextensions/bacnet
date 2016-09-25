package org.openmucextensions.driver.bacnet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;

/**
 * A singleton factory class to handle instances of local BACnet devices.
 * 
 * @author Lechner, Pichler
 *
 */
public class LocalDeviceFactory {
	
	private LocalDeviceFactory() { } // private constructor for singleton
	
	private static LocalDeviceFactory INSTANCE = null;
	
	private final static Logger LOGGER = LoggerFactory.getLogger(BACnetDriver.class);
	private final Map<Integer, LocalDevice> localDevices = new ConcurrentHashMap<Integer, LocalDevice>();
	private final Map<Integer, Integer> localDeviceReferences = new HashMap<>();
	private int nextDeviceInstanceNumber = 10000;
	
	/**
	 * Gets a single <code>LocalDeviceFactory</code> instance.
	 * 
	 * @return a single <code>LocalDeviceFactory</code> instance
	 */
	public static LocalDeviceFactory getInstance() {
		if(INSTANCE==null) {
			synchronized (LocalDeviceFactory.class) {
				if(INSTANCE==null)
					INSTANCE = new LocalDeviceFactory();
			}
		}
		return INSTANCE;
	}

    /**
     * Gets a {@link LocalDevice} instance for the given port. The device might be created or a cached instance may be returned.
     * This localDevice must be dismissed if not used any more by calling {@link #dismissLocalDevice(Integer)}.
     * 
     * @param broadcastIP the broadcast IP of the local device or <code>null</code> (default: 255.255.255.255)
     * @param localBindAddress the local bind IP or <code>null</code> (default: 0.0.0.0)
     * @param localPort the local IP port or <code>null</code> (default: 0xBAC0)
     * @param deviceInstanceNumber the local device instance number or <code>null</code> (default: auto-increment starting from 10000)
     * @return a {@link LocalDevice} instance for the given port
     * @throws Exception if any error occurs while initializing the local device
     */
    LocalDevice obtainLocalDevice(String broadcastIP, String localBindAddress, Integer localPort, Integer deviceInstanceNumber) throws Exception {
        synchronized (localDevices) {
            localPort = (localPort != null) ? localPort : IpNetwork.DEFAULT_PORT;
            if(localDevices.containsKey(localPort)) {
                final LocalDevice existingLocalDevice = localDevices.get(localPort);
                final int existingInstanceId = existingLocalDevice.getConfiguration().getInstanceId();
                LOGGER.debug("reusing local BACnet device with instance number {} and port 0x{}", existingInstanceId, Integer.toHexString(localPort));
                if ((deviceInstanceNumber != null) && (!deviceInstanceNumber.equals(existingInstanceId))) {
                    LOGGER.warn("instance number of existing device differs from specified one! (configured: {}, used: {})", existingInstanceId, deviceInstanceNumber);
                }
                increaseDeviceReference(localPort);
                return existingLocalDevice;
            }
            
            broadcastIP = (broadcastIP != null) ? broadcastIP : IpNetwork.DEFAULT_BROADCAST_IP;
            localBindAddress = (localBindAddress != null) ? localBindAddress : IpNetwork.DEFAULT_BIND_IP;
            deviceInstanceNumber = (deviceInstanceNumber != null) ? deviceInstanceNumber : nextDeviceInstanceNumber++;
            
            final IpNetwork network = new IpNetworkBuilder().broadcastIp(broadcastIP).port(localPort).localBindAddress(localBindAddress).build();
            final Transport transport = new DefaultTransport(network);
            final LocalDevice device = new LocalDevice(deviceInstanceNumber, transport);
            device.initialize();
            
            LOGGER.debug("created local BACnet device with instance number {} and port 0x{}", deviceInstanceNumber, Integer.toHexString(localPort));
            
            localDevices.put(localPort, device);
            increaseDeviceReference(localPort);
            return device;
        }
    }

    private void increaseDeviceReference(int localPort) {
        synchronized(localDeviceReferences) {
            Integer numberOfReferences = localDeviceReferences.get(localPort);
            if (numberOfReferences == null)
                numberOfReferences = 0;
            localDeviceReferences.put(localPort, numberOfReferences + 1);
        }
    }

    /**
     * decrease the number of device references.
     * @param localPort
     * @return <code>true</code> if there are references left, 
     * <code>false</code> if there are no more references.
     */
    private boolean decreaseDeviceReference(int localPort) {
        synchronized(localDeviceReferences) {
            final Integer numberOfReferences = localDeviceReferences.get(localPort);
            if (numberOfReferences == null)
                return false;
            if (numberOfReferences == 1) {
                localDeviceReferences.remove(localPort);
                return false;
            }
            localDeviceReferences.put(localPort, numberOfReferences - 1);
            return true;
        }
    }

    /**
     * Dismiss the usage of a {@link LocalDevice}. This may simply reduce the reference-counter by one or if no
     * references are left terminate the device.
     * @param port
     */
    void dismissLocalDevice(Integer port) {
        synchronized (localDevices) {
            final boolean referencesLeft = decreaseDeviceReference(port);
            if (!referencesLeft) {
                // no more references to this device --> remove it from cache and terminate it
                final LocalDevice localDevice = localDevices.remove(port);
                LOGGER.debug("local device {} will be terminated", localDevice.getConfiguration().getInstanceId());
                localDevice.terminate();
            }
        }
    }

    /**
     * Dismiss all cached {@link LocalDevice}s.
     */
    void dismissAll() {
        Collection<Integer> ports = new ArrayList<Integer>(localDeviceReferences.keySet());
        for (Integer devicePort : ports) {
            dismissLocalDevice(devicePort);
        }
        localDevices.clear();
        localDeviceReferences.clear();
    }
	
}
