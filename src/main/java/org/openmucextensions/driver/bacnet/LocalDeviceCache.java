package org.openmucextensions.driver.bacnet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;

/**
 * A simple cache for {@link LocalDevice}s which contains a reference counter to check how often a single device
 * has been obtained. On dismiss the reference counter will be decreased and if there are no references any more,
 * the device will be terminated and removed from the cache.
 * @author daniel
 */
public class LocalDeviceCache {
    private final static Logger logger = LoggerFactory.getLogger(LocalDeviceCache.class);
    
    /** The cache of local devices. Key is the IP port number of the local device and value is the device */
    private final Map<Integer, LocalDevice> localDevices = new HashMap<Integer, LocalDevice>();
    /** Number of references of the local device stored in the map {@link #localDevices}. 
     *  (=how often {@link #obtainLocalDevice(String, String, Integer, Integer)}
     *  was called for this port). */
    private final Multiset<Integer> localDeviceReferences = HashMultiset.create();
    private int nextDeviceInstanceNumber = 10000;

    /**
     * Get a {@link LocalDevice} for the given port. The device might be created or a cached instance may be returned.
     * This localDevice must be dismissed if not used any more by calling {@link #dismissLocalDevice(Integer)}.
     */
    LocalDevice obtainLocalDevice(String broadcastIP, String localBindAddress, Integer localPort, Integer deviceInstanceNumber) throws Exception {
        synchronized (localDevices) {
            localPort = (localPort != null) ? localPort : IpNetwork.DEFAULT_PORT;
            if(localDevices.containsKey(localPort)) {
                final LocalDevice existingLocalDevice = localDevices.get(localPort);
                final int existingInstanceId = existingLocalDevice.getConfiguration().getInstanceId();
                logger.debug("reusing local BACnet device with instance number {} and port 0x{}", existingInstanceId, Integer.toHexString(localPort));
                if ((deviceInstanceNumber != null) && (!deviceInstanceNumber.equals(existingInstanceId))) {
                    logger.warn("instance number of existing device differs from specified one! (configured: {}, used: {})", existingInstanceId, deviceInstanceNumber);
                }
                localDeviceReferences.add(localPort);
                return existingLocalDevice;
            }
            
            broadcastIP = (broadcastIP != null) ? broadcastIP : IpNetwork.DEFAULT_BROADCAST_IP;
            localBindAddress = (localBindAddress != null) ? localBindAddress : IpNetwork.DEFAULT_BIND_IP;
            deviceInstanceNumber = (deviceInstanceNumber != null) ? deviceInstanceNumber : nextDeviceInstanceNumber++;
            
            final IpNetwork network = new IpNetworkBuilder().broadcastIp(broadcastIP).port(localPort).localBindAddress(localBindAddress).build();
            final Transport transport = new DefaultTransport(network);
            final LocalDevice device = new LocalDevice(deviceInstanceNumber, transport);
            device.initialize();
            
            logger.debug("created local BACnet device with instance number {} and port 0x{}", deviceInstanceNumber, Integer.toHexString(localPort));
            
            localDevices.put(localPort, device);
            localDeviceReferences.add(localPort);
            return device;
        }
    }

    /**
     * Dismiss the usage of a {@link LocalDevice}. This may simply reduce the reference-counter by one or if no
     * references are left terminate the device.
     * @param port
     */
    void dismissLocalDevice(Integer port) {
        synchronized (localDevices) {
            localDeviceReferences.remove(port);
            if (!localDeviceReferences.contains(port)) {
                // no more references to this device --> remove it from cache and terminate it
                final LocalDevice localDevice = localDevices.remove(port);
                logger.debug("local device {} will be terminated", localDevice.getConfiguration().getInstanceId());
                localDevice.terminate();
            }
        }
    }

    /**
     * Dismiss all cached {@link LocalDevice}s.
     */
    void dismissAll() {
        Collection<Integer> ports = new ArrayList<Integer>(localDeviceReferences.elementSet());
        for (Integer devicePort : ports) {
            dismissLocalDevice(devicePort);
        }
        localDevices.clear();
        localDeviceReferences.clear();
    }
}
