# bacnet4openmuc
OpenMUC BACnet/IP communication driver based on bacnet4J.

This project contains a communication driver for Fraunhofer's OpenMUC framework (see www.openmuc.org) that allows to communicate with BACnet communication networks. BACnet is a communication protocol mainly used in the building automation domain, specified by the American Society of Heating, Refrigerating and Air-Conditioning Engineers (ASHRAE), see www.bacnet.org for details.

The driver is based on (uses internally) the bacnet4J library version 1.3, which is hosted at http://sourceforge.net/projects/bacnet4j/. Bacause the documentation for the library is very poor, the wiki of this projects contains some examples for using the library.

### Feature overview:
* Read present value from basic BACnet object types like analog, binary or multitate values
* Write values to commandable objects with definable priority
* Scan for remote devices in a BACnet network (WhoIs)
* Scan for data points (channels) on a specified remote device
