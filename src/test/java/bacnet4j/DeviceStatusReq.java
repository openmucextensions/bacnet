package bacnet4j;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyAck;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedRequestService;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyRequest;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.enumerated.DeviceStatus;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;

public class DeviceStatusReq {

	private static int port = 0xBAC5;
	private static String remoteDeviceIpAddress = "10.78.20.115";
	private static int remoteDeviceIdentifier = 2138113;
	
	public static void main(String[] args) throws Throwable {
		
		IpNetwork network = new IpNetwork(IpNetwork.DEFAULT_BROADCAST_IP, port);
        Transport transport = new Transport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();

        RemoteDevice remoteDevice = localDevice.findRemoteDevice(new Address(remoteDeviceIpAddress, port), null, remoteDeviceIdentifier);
        
        ConfirmedRequestService request = new ReadPropertyRequest(remoteDevice.getObjectIdentifier(), PropertyIdentifier.systemStatus);
        ReadPropertyAck result = (ReadPropertyAck) localDevice.send(remoteDevice, request);
        DeviceStatus status = (DeviceStatus) result.getValue();
        
        System.out.println("Device status of device " + remoteDevice.getInstanceNumber() + ": " + status.toString());
        
        System.out.println("Remote device IP address: " + remoteDevice.getAddress().getMacAddress().getInetAddress().getCanonicalHostName());
        System.out.println("Max. read multiple references: " + remoteDevice.getMaxReadMultipleReferences());
        localDevice.terminate();
        
	}

}
