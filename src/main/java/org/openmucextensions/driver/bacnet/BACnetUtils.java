package org.openmucextensions.driver.bacnet;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;

public abstract class BACnetUtils {
    private final static Logger logger = LoggerFactory.getLogger(BACnetUtils.class);

    private BACnetUtils() {}
    
    // delimiter to separate object-name from property identifier (note: this delimiter is not allowed in BACnet names)
    public final static String OBJNAME_PROPERTY_DELIMITER = "#";

    public static ObjectType getObjectTypeByString(String objTypeStr) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final Field field = ObjectType.class.getField(objTypeStr);
        return (ObjectType) field.get(null);
    }

    public static Collection<String> getAllObjectTypesAsString() {
        // get all fields of the class with the correct type and extract the name
        return Arrays.stream(ObjectType.class.getFields())
            .filter(new FieldTypePredicate(ObjectType.class))
            .map(Field::getName)
            .collect(Collectors.toList());
    }

    public static EngineeringUnits getEngineeringUnitByString(String engineeringUnitStr) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final Field field = EngineeringUnits.class.getField(engineeringUnitStr);
        return (EngineeringUnits) field.get(null);
    }
    
    public static Collection<String> getAllEngineeringUnitsAsString() {
        // get all fields of the class with the correct type and extract the name
        return Arrays.stream(EngineeringUnits.class.getFields())
            .filter(new FieldTypePredicate(EngineeringUnits.class))
            .map(Field::getName)
            .collect(Collectors.toList());
    }

    /**
     * Predicate which filters by type of the {@link Field}.
     * @author daniel
     */
    private static class FieldTypePredicate implements Predicate<Field> {
        private final Class<?> filterType;
        
        private FieldTypePredicate(Class<?> filterType) {
            this.filterType = filterType;
        }
        
        @Override
        public boolean test(Field field) {
            return filterType.equals(field.getType());
        }
    }
    
    public static ObjectPropertyIdentification getNameAndPropertyType(final String channelAddress) {
        final int delimiterCount = StringUtils.countMatches(channelAddress, OBJNAME_PROPERTY_DELIMITER);
        if (delimiterCount > 1) {
            logger.error("invalid channelAddress {} - cannot create object", channelAddress);
            return null;
        }
        final String[] objNameSplit = channelAddress.split(OBJNAME_PROPERTY_DELIMITER);
        if (objNameSplit.length == 1)
            return new ObjectPropertyIdentification(channelAddress, PropertyIdentifier.presentValue);
        
        final String objName = objNameSplit[0];
        final String propName = objNameSplit[1];
        try {
            final PropertyIdentifier propertyIdentifier = PropertyIdentifierFactory.getPropertyIdentifier(propName);
            return new ObjectPropertyIdentification(objName, propertyIdentifier);
        }
        catch (IllegalArgumentException | SecurityException | ClassCastException e) {
            logger.error("invalid channelAddress {} because of wrong property identifier ({}) - cannot create object", channelAddress, e.getMessage());
            return null;
        }
    }
    
    public static class ObjectPropertyIdentification {
        private final String objectName;
        private final PropertyIdentifier property;
        
        public ObjectPropertyIdentification(String objectName, PropertyIdentifier property) {
            this.objectName = objectName;
            this.property = property;
        }

        public String getObjectName() {
            return objectName;
        }

        public PropertyIdentifier getProperty() {
            return property;
        }
    }
    
}
