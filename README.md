# BACnet Driver Service
OpenMUC BACnet/IP communication driver based on bacnet4J. The project is licensed under GPLv3.

This project contains a communication driver for Fraunhofer's OpenMUC framework (see www.openmuc.org) that allows to communicate with BACnet communication networks. BACnet is a communication protocol mainly used in the building automation domain, specified by the American Society of Heating, Refrigerating and Air-Conditioning Engineers (ASHRAE), see www.bacnet.org for details.

The driver is based on (uses internally) the bacnet4J library version 1.3, which is hosted at http://sourceforge.net/projects/bacnet4j/. The wiki of this projects contains some examples for using the bacnet4J library.

## Features
* Automated selection of IP network port
* Read present value from basic BACnet object types like analog, binary or multistate values
* Write values to commandable objects with definable priority
* Scan for remote devices in a BACnet network (WhoIs)
* Scan for data points (channels) on a specified remote device
* Add a listener for change-of-value (COV)
* BACnet server to serve BACnet objects (analog and binary values)

## BACnet Interoperability Building Blocks (BIBBs)

The driver covers the following BACnet interoperability building blocks:

* Data Sharing - ReadProperty-A (DS-RP-A)
* Data Sharing-ReadPropertyMultiple-A (DS-RPM-A)
* Data Sharing-WriteProperty-A (DS-WP-A)
* Data Sharing-WritePropertyMultiple-A (DS-WPM-A) (planned)
* Data Sharing-COVP-A (DS-COVP-A)
* Data Sharing-COV-Unsubscribed-A (DS-COVU-A)
* Device Management-Dynamic Device Binding-A (DM-DDB-A)

For further information see the project's [wiki](https://github.com/openmucextensions/bacnet/wiki).

## Example configuration

The driver is configurable via the `channels.xml` file. An example snippet for BACnet object server is shown here and explained in detail in the wiki.

```xml
    <driver id="bacnet">
        <device id="localserver">
            <deviceAddress>12345</deviceAddress>
            <settings>localDevicePort=0xBAC0;broadcastIP=172.16.162.255;isServer=true</settings>
            <connectRetryInterval>5m</connectRetryInterval>
            <channel id="FloatWert1">
                <channelAddress>L'Float1</channelAddress>
                <listening>true</listening>
                <unit>analogValue;degreesCelsius</unit>
            </channel>
            <channel id="FloatWert2">
                <channelAddress>L'Float2</channelAddress>
                <listening>true</listening>
                <unit>analogValue;degreesCelsius</unit>
            </channel>
            <channel id="BoolWert1">
                <channelAddress>L'Bool1</channelAddress>
                <unit>binaryValue;noUnits</unit>
            </channel>
            <channel id="BoolWert1_OOS">
                <channelAddress>L'Bool1#outOfService</channelAddress>
            </channel>
        </device>
     </driver>
``` 

