package org.openmucextensions.driver.bacnet;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;

public abstract class BACnetUtils {
    private BACnetUtils() {}
    
    public static ObjectType getObjectTypeByString(String objTypeStr) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final Field field = ObjectType.class.getField(objTypeStr);
        return (ObjectType) field.get(null);
    }

    public static Collection<String> getAllObjectTypesAsString() {
        // get all fields of the class with the correct type
        final Iterable<Field> fieldsWithCorrectType = 
                Iterables.filter(Arrays.asList(ObjectType.class.getFields()), new FieldTypePredicate(ObjectType.class));
        // extract the names
        final Iterable<String> fieldNames = Iterables.transform(fieldsWithCorrectType, new FieldNameFunction());
        return Lists.newArrayList(fieldNames);
    }

    public static EngineeringUnits getEngineeringUnitByString(String engineeringUnitStr) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final Field field = EngineeringUnits.class.getField(engineeringUnitStr);
        return (EngineeringUnits) field.get(null);
    }
    
    public static Collection<String> getAllEngineeringUnitsAsString() {
        // get all fields of the class with the correct type
        final Iterable<Field> fieldsWithCorrectType = 
                Iterables.filter(Arrays.asList(EngineeringUnits.class.getFields()), new FieldTypePredicate(EngineeringUnits.class));
        // extract the names
        final Iterable<String> fieldNames = Iterables.transform(fieldsWithCorrectType, new FieldNameFunction());
        return Lists.newArrayList(fieldNames);
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
        public boolean apply(Field field) {
            return filterType.equals(field.getType());
        }
    }
    
    private static class FieldNameFunction implements Function<Field, String> {
        @Override
        public String apply(Field input) {
            return input.getName();
        }
    }
    
}
