package org.openmucextensions.driver.bacnet;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;

public abstract class BACnetUtils {
    private BACnetUtils() {}
    
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
}
