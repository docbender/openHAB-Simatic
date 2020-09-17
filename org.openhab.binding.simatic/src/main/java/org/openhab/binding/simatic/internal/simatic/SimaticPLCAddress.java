/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal.simatic;

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

    // final String address;
    final Integer addressByte;
    final Integer addressBit;
    final Integer dBNum;
    final boolean floatNumber;
    SimaticPLCAreaTypes area = SimaticPLCAreaTypes.UNKNOWN_AREA;
    SimaticPLCDataTypes dataType = SimaticPLCDataTypes.UNKNOWN_TYPE;
    final Integer dataLength;
    // @Nullable SimaticTypes datatype;

    /*
     * public SimaticPLCAddress(String address) {
     * this.address = address;
     *
     * this.dataLength = prepareAddress();
     * }
     *
     * public SimaticPLCAddress(String address, int dataLength) {
     * this.address = address;
     * this.dataLength = dataLength;
     * prepareAddress();
     * }
     */

    /**
     * Constructor NonDatablock area like MB10
     *
     * @param nonDbArea Area type (MB,IW,MD,...)
     * @param bytePosition Data offset
     */
    public SimaticPLCAddress(String nonDbArea, int bytePosition) {
        this(nonDbArea, bytePosition, false);
    }

    /**
     * Constructor NonDatablock area like MB10
     *
     * @param nonDbArea Area type (MB,IW,MD,...)
     * @param bytePosition Data offset
     * @param isFloat Is target float number
     */
    public SimaticPLCAddress(String nonDbArea, int bytePosition, boolean isFloat) {
        addressByte = bytePosition;
        addressBit = 0;
        dBNum = 0;
        floatNumber = isFloat;

        dataLength = prepareAddress(nonDbArea);
    }

    /**
     * Constructor NonDatablock bit area like M10.1
     *
     * @param nonDbArea Area type (M,I,Q,A,E)
     * @param bytePosition Data byte offset
     * @param bitPosition Data bit offset
     */
    public SimaticPLCAddress(String nonDbArea, int bytePosition, int bitPosition) {
        addressByte = bytePosition;
        addressBit = bitPosition;
        dBNum = 0;
        floatNumber = false;

        dataLength = prepareAddress(nonDbArea);
    }

    /**
     * Constructor NonDatablock array area like MB10
     *
     * @param bytePosition Data offset
     * @param length Data length
     */
    public SimaticPLCAddress(String nonDbArea, int bytePosition, int bitPosition, int length) {
        addressByte = bytePosition;
        addressBit = 0;
        dBNum = 0;
        floatNumber = false;

        dataLength = length;
        prepareAddress(nonDbArea);
    }

    /**
     * Constructor Datablock area like DB1.DBB10
     *
     * @param dbNumber Datablock number
     * @param dbArea Area type (D,B,W)
     * @param bytePosition Data offset
     */
    public SimaticPLCAddress(int dbNumber, String dbArea, int bytePosition) {
        this(dbNumber, dbArea, bytePosition, false);
    }

    /**
     * Constructor Datablock area like DB1.DBB10
     *
     * @param dbNumber Datablock number
     * @param dbArea Area type (D,B,W)
     * @param bytePosition Data offset
     * @param isFloat Is target float number
     */
    public SimaticPLCAddress(int dbNumber, String dbArea, int bytePosition, boolean isFloat) {
        addressByte = bytePosition;
        addressBit = 0;
        dBNum = dbNumber;
        floatNumber = isFloat;

        dataLength = prepareAddress(dbArea);
    }

    /**
     * Constructor Datablock bit area like DB1.DBX10.0
     *
     * @param dbNumber Datablock number
     * @param bytePosition Data byte offset
     * @param bitPosition Data bit offset *
     */
    public SimaticPLCAddress(int dbNumber, int bytePosition, int bitPosition) {
        addressByte = bytePosition;
        addressBit = bitPosition;
        dBNum = dbNumber;
        floatNumber = false;

        dataLength = prepareAddress("B");
    }

    /**
     * Constructor Datablock array area like DB1.DBB10
     *
     * @param dbNumber Datablock number
     * @param bytePosition Data offset
     * @param length Data length
     */
    public SimaticPLCAddress(int dbNumber, int bytePosition, int bitPosition, int length) {
        addressByte = bytePosition;
        addressBit = 0;
        dBNum = dbNumber;
        floatNumber = false;

        dataLength = length;
        prepareAddress("B");
    }

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
        return dBNum;
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
     * Prepare Simatic address from string representation
     *
     * @return Returns datatype length [bytes]
     */
    int prepareAddress(String sArea) {
        try {
            if (sArea.equalsIgnoreCase("MD")) {
                area = SimaticPLCAreaTypes.M;
                dataType = SimaticPLCDataTypes.DWORD;
                return 4;
            } else if (sArea.equalsIgnoreCase("MW")) {
                area = SimaticPLCAreaTypes.M;
                dataType = SimaticPLCDataTypes.WORD;
                return 2;
            } else if (sArea.equalsIgnoreCase("MB")) {
                area = SimaticPLCAreaTypes.M;
                dataType = SimaticPLCDataTypes.BYTE;
                return 1;
            } else if (sArea.equalsIgnoreCase("M")) {
                area = SimaticPLCAreaTypes.M;
                dataType = SimaticPLCDataTypes.BIT;

                return 1;
            } else if (sArea.equalsIgnoreCase("ID") || sArea.equalsIgnoreCase("ED")) {
                area = SimaticPLCAreaTypes.I;
                dataType = SimaticPLCDataTypes.DWORD;

                return 4;
            } else if (sArea.equalsIgnoreCase("IW") || sArea.equalsIgnoreCase("EW")) {
                area = SimaticPLCAreaTypes.I;
                dataType = SimaticPLCDataTypes.WORD;

                return 2;
            } else if (sArea.equalsIgnoreCase("IB") || sArea.equalsIgnoreCase("EB")) {
                area = SimaticPLCAreaTypes.I;
                dataType = SimaticPLCDataTypes.BYTE;

                return 1;
            } else if (sArea.equalsIgnoreCase("I") || sArea.equalsIgnoreCase("E")) {
                area = SimaticPLCAreaTypes.I;
                dataType = SimaticPLCDataTypes.BIT;

                return 1;
            } else if (sArea.equalsIgnoreCase("QD") || sArea.equalsIgnoreCase("AD")) {
                area = SimaticPLCAreaTypes.Q;
                dataType = SimaticPLCDataTypes.DWORD;

                return 4;
            } else if (sArea.equalsIgnoreCase("QW") || sArea.equalsIgnoreCase("AW")) {
                area = SimaticPLCAreaTypes.Q;
                dataType = SimaticPLCDataTypes.WORD;

                return 2;
            } else if (sArea.equalsIgnoreCase("QB") || sArea.equalsIgnoreCase("AB")) {
                area = SimaticPLCAreaTypes.Q;
                dataType = SimaticPLCDataTypes.BYTE;

                return 1;
            } else if (sArea.equalsIgnoreCase("Q") || sArea.equalsIgnoreCase("A")) {
                area = SimaticPLCAreaTypes.Q;
                dataType = SimaticPLCDataTypes.BIT;

                return 1;
            } else if (sArea.equalsIgnoreCase("D")) {
                area = SimaticPLCAreaTypes.DB;
                dataType = SimaticPLCDataTypes.DWORD;
                return 4;
            } else if (sArea.equalsIgnoreCase("W")) {
                area = SimaticPLCAreaTypes.DB;
                dataType = SimaticPLCDataTypes.WORD;
                return 2;
            } else if (sArea.equalsIgnoreCase("B")) {
                area = SimaticPLCAreaTypes.DB;
                dataType = SimaticPLCDataTypes.BYTE;
                return 1;
            } else if (sArea.equalsIgnoreCase("X")) {
                area = SimaticPLCAreaTypes.DB;
                dataType = SimaticPLCDataTypes.BIT;
                return 1;
            } else {
                area = SimaticPLCAreaTypes.UNKNOWN_AREA;
                throw new Exception("Invalid area");
            }

        } catch (Exception ex) {
            logger.warn("Invalid address " + sArea + " (" + ex.getMessage() + ")");
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
        int dbResult = this.dBNum.compareTo(obj.dBNum);
        int addrResult = this.addressByte.compareTo(obj.addressByte);
        int lenresult = this.dataLength.compareTo(obj.dataLength);

        return areaResult == 0
                ? (dbResult == 0
                        ? (addrResult == 0 ? (lenresult == 0 ? this.addressBit.compareTo(obj.addressBit) : lenresult)
                                : addrResult)
                        : dbResult)
                : areaResult;
    }

    public int getDataLength() {
        return dataLength;
    }

    public SimaticPLCDataTypes getSimaticDataType() {
        return dataType;
    }

    public boolean isFloat() {
        return floatNumber;
    }

    @Override
    public String toString() {
        if (area == SimaticPLCAreaTypes.DB) {
            if (dataType == SimaticPLCDataTypes.BIT) {
                return "DB" + dBNum + ".DBX" + addressByte + "." + addressBit;
            } else if (dataType == SimaticPLCDataTypes.DWORD) {
                return "DB" + dBNum + ".DBD" + addressByte;
            } else if (dataType == SimaticPLCDataTypes.WORD) {
                return "DB" + dBNum + ".DBW" + addressByte;
            } else {
                return "DB" + dBNum + ".DBB" + addressByte;
            }
        } else if (dataType == SimaticPLCDataTypes.BIT) {
            return area.toString() + addressByte + "." + addressBit;
        } else if (dataType == SimaticPLCDataTypes.DWORD) {
            return area.toString() + "D" + addressByte;
        } else if (dataType == SimaticPLCDataTypes.WORD) {
            return area.toString() + "W" + addressByte;
        } else {
            return area.toString() + "B" + addressByte;
        }
    }
}
