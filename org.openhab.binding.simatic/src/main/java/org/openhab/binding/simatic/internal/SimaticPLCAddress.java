/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PLC address class
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimaticPLCAddress implements Comparable {
    private static final Logger logger = LoggerFactory.getLogger(SimaticPLCAddress.class);

    final String address;
    Integer addressByte = 0;
    Integer addressBit = 0;
    Integer DBNum = 0;
    SimaticPLCAreaTypes area = SimaticPLCAreaTypes.UNKNOWN_AREA;
    SimaticPLCDataTypes dataType = SimaticPLCDataTypes.UNKNOWN_TYPE;
    Integer dataLength;

    public SimaticPLCAddress(String address) {
        this.address = address;

        this.dataLength = prepareAddress();
    }

    public SimaticPLCAddress(String address, int dataLength) {
        this.address = address;
        this.dataLength = dataLength;
        prepareAddress();
    }

    // public int getDaveArea() {
    // switch (area) {
    // case M:
    // return libnodave.daveFlags;
    // case DB:
    // return libnodave.daveDB;
    // case I:
    // return libnodave.daveInputs;
    // case Q:
    // return libnodave.daveOutputs;
    // case UNKNOWN_AREA:
    // default:
    // return -1;
    // }
    // }

    public int getByteOffset() {
        return addressByte;
    }

    public int getBitOffset() {
        return addressBit;
    }

    /**
     * Return DB number
     *
     * @return
     */
    public int getDBNumber() {
        return DBNum;
    }

    public SimaticPLCAreaTypes getArea() {
        return area;
    }

    public static boolean ValidateAddress(String address) {

        Matcher matcher = Pattern
                .compile("^([IQMAE]\\d*[.][0-7]|[IQMAE][BWD]\\d*|((DB)\\d*[.](DB)([BWD]\\d*|[X]\\d*[.][0-7])))$")
                .matcher(address);

        return matcher.matches();
    }

    /**
     * Prepare simatic address from string representation
     *
     * @return Returns datatype length [bytes]
     */
    int prepareAddress() {
        try {
            if (address.startsWith("MD")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.M;
                dataType = SimaticPLCDataTypes.DWORD;

                return 4;
            } else if (address.startsWith("MW")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.M;
                dataType = SimaticPLCDataTypes.WORD;
                return 2;
            } else if (address.startsWith("MB")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.M;
                dataType = SimaticPLCDataTypes.BYTE;
                return 1;
            } else if (address.startsWith("M")) {
                String[] items = address.split("\\.");

                if (items.length != 2) {
                    throw new Exception("No bit area");
                }

                addressByte = Integer.parseInt(items[0].substring(1, items[0].length()));
                addressBit = Integer.parseInt(items[1]);

                items = null;

                area = SimaticPLCAreaTypes.M;
                dataType = SimaticPLCDataTypes.BIT;

                return 1;
            } else if (address.startsWith("ID") || address.startsWith("ED")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.I;
                dataType = SimaticPLCDataTypes.DWORD;

                return 4;
            } else if (address.startsWith("IW") || address.startsWith("EW")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.I;
                dataType = SimaticPLCDataTypes.WORD;

                return 2;
            } else if (address.startsWith("IB") || address.startsWith("EB")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.I;
                dataType = SimaticPLCDataTypes.BYTE;

                return 1;
            } else if (address.startsWith("I") || address.startsWith("E")) {
                String[] items = address.split("\\.");

                if (items.length != 2) {
                    throw new Exception("No bit area");
                }

                addressByte = Integer.parseInt(items[0].substring(1, items[0].length()));
                addressBit = Integer.parseInt(items[1]);

                items = null;

                area = SimaticPLCAreaTypes.I;
                dataType = SimaticPLCDataTypes.BIT;

                return 1;
            } else if (address.startsWith("QD") || address.startsWith("AD")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.Q;
                dataType = SimaticPLCDataTypes.DWORD;

                return 4;
            } else if (address.startsWith("QW") || address.startsWith("AW")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.Q;
                dataType = SimaticPLCDataTypes.WORD;

                return 2;
            } else if (address.startsWith("QB") || address.startsWith("AB")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.Q;
                dataType = SimaticPLCDataTypes.BYTE;

                return 1;
            } else if (address.startsWith("Q") || address.startsWith("A")) {
                String[] items = address.split("\\.");

                if (items.length != 2) {
                    throw new Exception("No bit area");
                }

                addressByte = Integer.parseInt(items[0].substring(1, items[0].length()));
                addressBit = Integer.parseInt(items[1]);

                items = null;

                area = SimaticPLCAreaTypes.Q;
                dataType = SimaticPLCDataTypes.BIT;

                return 1;
            } else if (address.startsWith("DB")) {
                String[] items = address.split("\\.");

                if (items.length < 2) {
                    throw new Exception("Short DB address");
                }

                area = SimaticPLCAreaTypes.DB;
                DBNum = Integer.parseInt(items[0].substring(2));
                String tmp = items[1];

                if (tmp.startsWith("DBD")) {
                    addressByte = Integer.parseInt(tmp.substring(3));
                    dataType = SimaticPLCDataTypes.DWORD;
                    return 4;
                } else if (tmp.startsWith("DBW")) {
                    addressByte = Integer.parseInt(tmp.substring(3));
                    dataType = SimaticPLCDataTypes.WORD;
                    return 2;
                } else if (tmp.startsWith("DBB")) {
                    addressByte = Integer.parseInt(tmp.substring(3));
                    dataType = SimaticPLCDataTypes.WORD;
                    return 1;
                } else if (tmp.startsWith("DBX")) {
                    if (items.length < 3) {
                        throw new Exception("No bit area");
                    }

                    addressByte = Integer.parseInt(tmp.substring(3, tmp.length()));
                    addressBit = Integer.parseInt(items[2]);
                    dataType = SimaticPLCDataTypes.BIT;
                    return 1;
                } else {
                    area = SimaticPLCAreaTypes.UNKNOWN_AREA;
                    throw new Exception("Invalid area");
                }

            } else {
                area = SimaticPLCAreaTypes.UNKNOWN_AREA;
                throw new Exception("Invalid area");
            }
        } catch (Exception ex) {
            logger.warn("Invalid address " + address + " (" + ex.getMessage() + ")");
        }

        return 1;
    }

    @Override
    public int compareTo(Object arg0) {
        if (!(arg0 instanceof SimaticPLCAddress)) {
            return -1;
        }

        SimaticPLCAddress obj = (SimaticPLCAddress) arg0;
        int areaResult = this.area.compareTo(obj.area);
        int dbResult = this.DBNum.compareTo(obj.DBNum);
        int addrResult = this.addressByte.compareTo(obj.addressByte);

        return areaResult == 0 ? (dbResult == 0
                ? (addrResult == 0 ? this.addressBit.compareTo(obj.addressBit) : addrResult) : dbResult) : areaResult;
    }

    public int getDataLength() {
        return dataLength;
    }

    @Override
    public String toString() {
        if (area == SimaticPLCAreaTypes.DB) {
            if (dataType == SimaticPLCDataTypes.BIT) {
                return address + "/DB" + DBNum + ".DBX" + addressByte + "." + addressBit;
            } else if (dataType == SimaticPLCDataTypes.DWORD) {
                return address + "/DB" + DBNum + ".DBD" + addressByte;
            } else if (dataType == SimaticPLCDataTypes.WORD) {
                return address + "/DB" + DBNum + ".DBW" + addressByte;
            } else {
                return address + "/DB" + DBNum + ".DBB" + addressByte;
            }
        } else if (dataType == SimaticPLCDataTypes.BIT) {
            return address + "/" + area.toString() + addressByte + "." + addressBit;
        } else if (dataType == SimaticPLCDataTypes.DWORD) {
            return address + "/" + area.toString() + "D" + addressByte;
        } else if (dataType == SimaticPLCDataTypes.WORD) {
            return address + "/" + area.toString() + "W" + addressByte;
        } else {
            return address + "/" + area.toString() + "B" + addressByte;
        }
    }
}
