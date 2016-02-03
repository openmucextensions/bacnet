package org.openmucextensions.driver.bacnet;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.openmuc.framework.data.BooleanValue;
import org.openmuc.framework.data.FloatValue;
import org.openmuc.framework.data.IntValue;
import org.openmuc.framework.data.StringValue;
import org.openmuc.framework.data.Value;
import org.openmuc.framework.data.ValueType;

import com.serotonin.bacnet4j.obj.AnalogValueObject;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.obj.BinaryValueObject;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.enumerated.BinaryPV;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.primitive.Null;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

public abstract class ConversionUtil {
    private ConversionUtil() {}
    
    private static final Map<ObjectType, ValueType> BACNET_2_OPENMUCMUC_TYPEMAPPING;
    
    static {
        BACNET_2_OPENMUCMUC_TYPEMAPPING = new HashMap<ObjectType, ValueType>();
        BACNET_2_OPENMUCMUC_TYPEMAPPING.put(ObjectType.analogInput, ValueType.FLOAT);
        BACNET_2_OPENMUCMUC_TYPEMAPPING.put(ObjectType.analogOutput, ValueType.FLOAT);
        BACNET_2_OPENMUCMUC_TYPEMAPPING.put(ObjectType.analogValue, ValueType.FLOAT);
        BACNET_2_OPENMUCMUC_TYPEMAPPING.put(ObjectType.binaryInput, ValueType.BOOLEAN);
        BACNET_2_OPENMUCMUC_TYPEMAPPING.put(ObjectType.binaryOutput, ValueType.BOOLEAN);
        BACNET_2_OPENMUCMUC_TYPEMAPPING.put(ObjectType.binaryValue, ValueType.BOOLEAN);
        BACNET_2_OPENMUCMUC_TYPEMAPPING.put(ObjectType.multiStateInput, ValueType.INTEGER);
    }
    
    public static ValueType getValueTypeForObjectType(ObjectType ot) {
        return BACNET_2_OPENMUCMUC_TYPEMAPPING.get(ot);
    }
    
    /**
     * Convert a BACnet value to an OpenMUC value
     * @param value The value coming from the BACnet driver
     * @param objectType The type of the BACnet value (must not be <code>null</code>).
     * @return the OpenMUC-representation of the value
     */
    public static Value convertValue(Encodable value, ObjectType objectType) {
        Objects.requireNonNull(objectType, "objectType must not be null");
        final ValueType openMUCType = BACNET_2_OPENMUCMUC_TYPEMAPPING.get(objectType);
        if (openMUCType == null)
            // default behavior in case of not handled object type
            return new StringValue(value.toString());

        switch (openMUCType) {
        case FLOAT:
            Real realValue = (Real) value;
            return new FloatValue(realValue.floatValue());
        case BOOLEAN:
            BinaryPV booleanValue = (BinaryPV) value;
            return (booleanValue.intValue()==0) ? new BooleanValue(false) : new BooleanValue(true);
        case INTEGER:
            UnsignedInteger integerValue = (UnsignedInteger) value;
            return new IntValue(integerValue.intValue());
        default:
            throw new InternalError("Reached default-branch of conversion-switch");
        }
    }
    
    /**
     * Convert an OpenMUC value to it's BACnet representation
     * @param value The value coming from the OpenMUC framework
     * @param objectType The BACnet type the value should be "converted" to (must not be <code>null</code>).
     * @return the BACnet-representation of the value
     * @throws IllegalArgumentException if the objectType is unknown (or not implemented yet)
     */
    public static Encodable convertValue(Value value, ObjectType objectType) {
        Objects.requireNonNull(objectType, "objectType must not be null");
        
        // value null means to release the command in the priority list
        if(value == null) return new Null();

        final ValueType openMUCType = BACNET_2_OPENMUCMUC_TYPEMAPPING.get(objectType);
        if (openMUCType == null)
            throw new IllegalArgumentException("cannot handle ObjectType " + objectType + " when converting value");
        switch (openMUCType) {
        case FLOAT:
            return new Real(value.asFloat());
        case BOOLEAN:
            return new BinaryPV(value.asInt());
        case INTEGER:
            return new UnsignedInteger(value.asInt());
        default:
            throw new InternalError("Reached default-branch of conversion-switch");
        }
    }
    
    public static BinaryPV convertBoolean(boolean value) {
        return (value) ? BinaryPV.active : BinaryPV.inactive;
    }
    
    public static BACnetObject createBACnetObject(ObjectType objectType, int instanceNumber, String objectName, String presentValue, EngineeringUnits unit) {
        Objects.requireNonNull(objectType, "objectType must not be null");
        final ValueType openMUCType = BACNET_2_OPENMUCMUC_TYPEMAPPING.get(objectType);

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
                presentValueBoolean = Boolean.parseBoolean(presentValue);
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
