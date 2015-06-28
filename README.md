# BACnet Driver Service
OpenMUC BACnet/IP communication driver based on bacnet4J. The project is licensed under GPLv3.

This project contains a communication driver for Fraunhofer's OpenMUC framework (see www.openmuc.org) that allows to communicate with BACnet communication networks. BACnet is a communication protocol mainly used in the building automation domain, specified by the American Society of Heating, Refrigerating and Air-Conditioning Engineers (ASHRAE), see www.bacnet.org for details.

The driver is based on (uses internally) the bacnet4J library version 1.3, which is hosted at http://sourceforge.net/projects/bacnet4j/. The wiki of this projects contains some examples for using the bacnet4J library.

## Features
* Automated selection of IP network port
* Read present value from basic BACnet object types like analog, binary or multitate values
* Write values to commandable objects with definable priority
* Scan for remote devices in a BACnet network (WhoIs)
* Scan for data points (channels) on a specified remote device
* Add a listener for change-of-value (COV)

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
