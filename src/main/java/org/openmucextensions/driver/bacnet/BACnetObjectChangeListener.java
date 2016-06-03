package org.openmucextensions.driver.bacnet;

import java.util.ArrayList;
import java.util.List;

import org.openmuc.framework.data.Flag;
import org.openmuc.framework.data.Record;
import org.openmuc.framework.driver.spi.ChannelRecordContainer;
import org.openmuc.framework.driver.spi.RecordsReceivedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.obj.BACnetObjectListener;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;

/**
 * Listener for changes of property values of a BACnet object. On notification the notification
 * is wrapped and forwarded to OpenMUC. 
 * 
 * @author daniel
 */
public class BACnetObjectChangeListener implements BACnetObjectListener {
    private final static Logger logger = LoggerFactory.getLogger(BACnetObjectChangeListener.class);
    private final RecordsReceivedListener listener;
    private final ChannelRecordContainer channelRecordContainer;
    private final ObjectType objectType;
    
    public BACnetObjectChangeListener(RecordsReceivedListener listener, ChannelRecordContainer channelRecordContainer, ObjectType objectType) {
        this.listener = listener;
        this.channelRecordContainer = channelRecordContainer;
        this.objectType = objectType;
    }

    @Override
    public void propertyChange(PropertyIdentifier pid, Encodable oldValue, Encodable newValue) {
        if (!pid.equals(PropertyIdentifier.presentValue)) {
            // currently not implemented
            logger.info("Channel {}/{}: propertyChange notification of {} not implemented yet", channelRecordContainer.getChannel().getId(), channelRecordContainer.getChannelAddress(), pid);
            return;
        }
        logger.debug("Channel {}/{}: propertyChange notification of {} from value {} to {}", channelRecordContainer.getChannel().getId(), channelRecordContainer.getChannelAddress(),
                pid, oldValue, newValue);
        final Record record = new Record(ConversionUtil.convertValue(newValue, objectType), 
                new Long(System.currentTimeMillis()), Flag.VALID);

        channelRecordContainer.setRecord(record);
        List<ChannelRecordContainer> containers = new ArrayList<ChannelRecordContainer>();
        containers.add(channelRecordContainer);
        listener.newRecords(containers);
    }
}
