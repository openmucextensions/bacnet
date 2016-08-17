package org.openmucextensions.driver.bacnet;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.openmuc.framework.data.BooleanValue;
import org.openmuc.framework.data.DoubleValue;
import org.openmuc.framework.data.FloatValue;
import org.openmuc.framework.data.IntValue;
import org.openmuc.framework.data.StringValue;
import org.openmuc.framework.data.Value;
import org.openmuc.framework.data.ValueType;

import com.serotonin.bacnet4j.obj.AnalogValueObject;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.obj.BinaryValueObject;
import com.serotonin.bacnet4j.obj.ObjectProperties;
import com.serotonin.bacnet4j.obj.PropertyTypeDefinition;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.enumerated.BinaryPV;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.Double;
import com.serotonin.bacnet4j.type.primitive.Null;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

public abstract class ConversionUtil {
    private ConversionUtil() {}
    
    private static final Map<Class<? extends Encodable>, ValueType> BACNET_2_OPENMUCMUC_TYPEMAPPING;
    
    static {
        BACNET_2_OPENMUCMUC_TYPEMAPPING = new HashMap<Class<? extends Encodable>, ValueType>();
        BACNET_2_OPENMUCMUC_TYPEMAPPING.put(Real.class, ValueType.FLOAT);
        BACNET_2_OPENMUCMUC_TYPEMAPPING.put(Double.class, ValueType.DOUBLE);
        BACNET_2_OPENMUCMUC_TYPEMAPPING.put(BinaryPV.class, ValueType.BOOLEAN);
        BACNET_2_OPENMUCMUC_TYPEMAPPING.put(Boolean.class, ValueType.BOOLEAN);
        BACNET_2_OPENMUCMUC_TYPEMAPPING.put(UnsignedInteger.class, ValueType.INTEGER);
    }
    
    public static ValueType getValueTypeMapping(Class<? extends Encodable> encodableClass) {
        return BACNET_2_OPENMUCMUC_TYPEMAPPING.get(encodableClass);
    }
    
    /**
     * Convert a BACnet value to an OpenMUC value
     * @param value The value coming from the BACnet driver
     * @param typeDefinition The type of the (source) BACnet value (must not be <code>null</code>).
     * @return the OpenMUC-representation of the value
     */
    public static Value convertValue(Encodable value, PropertyTypeDefinition typeDefinition) {
        Objects.requireNonNull(typeDefinition, "typeDefinition must not be null");
        
        final Class<? extends Encodable> bacnetType = typeDefinition.getClazz();
        final ValueType openMUCType = BACNET_2_OPENMUCMUC_TYPEMAPPING.get(bacnetType);
        if (openMUCType == null)
            // default behavior in case of not handled object type
            return new StringValue(value.toString());

        switch (openMUCType) {
        case FLOAT:
            if (bacnetType.equals(Real.class)) {
                Real realValue = (Real) value;
                return new FloatValue(realValue.floatValue());
            }
            break;
        case BOOLEAN:
            if (bacnetType.equals(BinaryPV.class)) {
                BinaryPV booleanValue = (BinaryPV) value;
                return (booleanValue.intValue()==0) ? new BooleanValue(false) : new BooleanValue(true);
            }
            if (bacnetType.equals(Boolean.class)) {
                Boolean booleanValue = (Boolean) value;
                return new BooleanValue(booleanValue.booleanValue());
            }
            break;
        case INTEGER:
            if (bacnetType.equals(UnsignedInteger.class)) {
                UnsignedInteger integerValue = (UnsignedInteger) value;
                return new IntValue(integerValue.intValue());
            }
            break;
        case DOUBLE:
            if (bacnetType.equals(Double.class)) {
                Double doubleValue = (Double) value;
                return new DoubleValue(doubleValue.doubleValue());
            }
            break;
        default:
            throw new InternalError("Reached default-branch of conversion-switch");
        }
        throw new InternalError("Program-error: conversion from " + openMUCType + " to " + bacnetType + " not implemented");
    }
    
    /**
     * Convert an OpenMUC value to it's BACnet representation
     * @param value The value coming from the OpenMUC framework
     * @param typeDefinition The BACnet property the value should be "converted" to (must not be <code>null</code>).
     * @return the BACnet-representation of the value
     * @throws IllegalArgumentException if the objectType is unknown (or not implemented yet)
     */
    public static Encodable convertValue(Value value, PropertyTypeDefinition typeDefinition) {
        Objects.requireNonNull(typeDefinition, "typeDefinition must not be null");
        
        // value null means to release the command in the priority list
        if(value == null) return new Null();

        final Class<? extends Encodable> bacnetType = typeDefinition.getClazz();
        final ValueType openMUCType = BACNET_2_OPENMUCMUC_TYPEMAPPING.get(bacnetType);
        if (openMUCType == null)
            throw new IllegalArgumentException("cannot handle type " + bacnetType.getName() + " when converting value");
        switch (openMUCType) {
        case FLOAT:
            if (bacnetType.equals(Real.class)) {
                return new Real(value.asFloat());
            }
            break;
        case BOOLEAN:
            if (bacnetType.equals(BinaryPV.class)) {
                return new BinaryPV(value.asInt());
            }
            if (bacnetType.equals(Boolean.class)) {
                return new Boolean(value.asBoolean());
            }
            break;
        case INTEGER:
            if (bacnetType.equals(UnsignedInteger.class)) {
                return new UnsignedInteger(value.asInt());
            }
            break;
        case DOUBLE:
            if (bacnetType.equals(Double.class)) {
                return new Double(value.asDouble());
            }
            break;
        default:
            throw new InternalError("Reached default-branch of conversion-switch");
        }
        throw new InternalError("Program-error: conversion from " + bacnetType + " to " + openMUCType + " not implemented");
    }
    
    public static BinaryPV convertBoolean(boolean value) {
        return (value) ? BinaryPV.active : BinaryPV.inactive;
    }
    
    public static BACnetObject createBACnetObject(ObjectType objectType, int instanceNumber, String objectName, String presentValue, EngineeringUnits unit) {
        Objects.requireNonNull(objectType, "objectType must not be null");
        final PropertyTypeDefinition propTypeDef = ObjectProperties.getPropertyTypeDefinition(objectType, PropertyIdentifier.presentValue);
        final ValueType openMUCType = BACNET_2_OPENMUCMUC_TYPEMAPPING.get(propTypeDef.getClazz());

        switch (openMUCType) {
        case FLOAT: {
            final float presentValueFloat;
            try {
                presentValueFloat = Float.parseFloat(presentValue);
            }
            catch (NumberFormatException nfe) {
                throw new IllegalArgumentException(String.format("cannot convert presentValue {} to float", presentValue));
            }
            final AnalogValueObject object = new AnalogValueObject(instanceNumber, objectName, presentValueFloat, unit, false);
            object.supportCommandable(new Real(10));
            // TODO read cov threshold from configuration
            object.supportCovReporting(1);
            return object;
        }
        case BOOLEAN: {
            final boolean presentValueBoolean;
            try {
                presentValueBoolean = java.lang.Boolean.parseBoolean(presentValue);
            }
            catch (NumberFormatException nfe) {
                throw new IllegalArgumentException(String.format("cannot convert presentValue {} to boolean", presentValue));
            }
            final BinaryPV presentValueBool = ConversionUtil.convertBoolean(presentValueBoolean);
            final BinaryValueObject object = new BinaryValueObject(instanceNumber, objectName, presentValueBool, false);
            object.supportCommandable(new com.serotonin.bacnet4j.type.primitive.Boolean(false));
            object.supportCovReporting();
            return object;
        }
        default:
            throw new IllegalArgumentException("cannot create BACnet object for type " + objectType);
        }
    }
}
