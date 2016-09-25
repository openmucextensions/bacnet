package bacnet4j;

import java.util.ArrayList;
import java.util.List;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.ServiceFuture;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkUtils;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyMultipleAck;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedRequestService;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyMultipleRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.constructed.ReadAccessResult;
import com.serotonin.bacnet4j.type.constructed.ReadAccessResult.Result;
import com.serotonin.bacnet4j.type.constructed.ReadAccessSpecification;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.util.DiscoveryUtils;
import com.serotonin.bacnet4j.util.RequestUtils;

public class MultipleRead {

	public static void main(String[] args) throws Exception {
		
		IpNetwork network = new IpNetwork(IpNetwork.DEFAULT_BROADCAST_IP, 0xBAC5);
        Transport transport = new DefaultTransport(network);
        
        int localDeviceID = 10000 + (int) ( Math.random() * 10000);
        LocalDevice localDevice = new LocalDevice(localDeviceID, transport);
        localDevice.initialize();

        RemoteDevice remoteDevice = localDevice.findRemoteDevice(IpNetworkUtils.toAddress("10.78.20.115", 0xbac5), 2138113);

        DiscoveryUtils.getExtendedDeviceInformation(localDevice, remoteDevice);
        List<ReadAccessSpecification> specifications = new ArrayList<ReadAccessSpecification>();
    	
    	@SuppressWarnings("unchecked")
		List<ObjectIdentifier> oids = ((SequenceOf<ObjectIdentifier>) RequestUtils.sendReadPropertyAllowNull(
                localDevice, remoteDevice, remoteDevice.getObjectIdentifier(), PropertyIdentifier.objectList)).getValues();

        for (ObjectIdentifier objectIdentifier : oids) {
        	// specifications.add(new ReadAccessSpecification(objectIdentifier, PropertyIdentifier.objectName));
        	if(objectIdentifier.getObjectType().equals(ObjectType.analogInput))
        		specifications.add(new ReadAccessSpecification(objectIdentifier, PropertyIdentifier.all));
		}
    	
//        ObjectIdentifier id1 = new ObjectIdentifier(ObjectType.schedule, 1);
//        ObjectIdentifier id2 = new ObjectIdentifier(ObjectType.pulseConverter, 1);
//        ObjectIdentifier id3 = new ObjectIdentifier(ObjectType.analogInput, 3);
        
        
//        specifications.add(new ReadAccessSpecification(id1, PropertyIdentifier.all));
//        specifications.add(new ReadAccessSpecification(id2, PropertyIdentifier.all));
        
        
        SequenceOf<ReadAccessSpecification> sequence = new SequenceOf<ReadAccessSpecification>(specifications);
        ConfirmedRequestService serviceRequest = new ReadPropertyMultipleRequest(sequence);
        
        ServiceFuture future = localDevice.send(remoteDevice, serviceRequest);
        ReadPropertyMultipleAck ack = future.get();
        
        SequenceOf<ReadAccessResult> results = ack.getListOfReadAccessResults();
        
        for (ReadAccessResult readAccessResult : results) {
        	
			System.out.println("*** Object identifier: " + readAccessResult.getObjectIdentifier() + " ***");
			
			for (Result result : readAccessResult.getListOfResults()) {
				System.out.println(result.getPropertyIdentifier() + ": " + result.getReadResult());
			}
			
			System.out.println("");
		}
        
        localDevice.terminate();
        
	}

}
