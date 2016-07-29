package org.openhab.binding.simatic.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimaticPLCAddress implements Comparable {

    final String address;
    Integer addressByte = 0;
    Integer addressBit = 0;
    Integer DBNum = 0;
    SimaticPLCAreaTypes area = SimaticPLCAreaTypes.UNKNOWN_AREA;
    SimaticPLCDataTypes dataType = SimaticPLCDataTypes.UNKNOWN_TYPE;

    public SimaticPLCAddress(String address) {
        this.address = address;

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

    public static boolean ValidateAddress(String address) {

        Matcher matcher = Pattern
                .compile("^([IQMAE]\\d*[.][0-7]|[IQMAE][BWD]\\d*|((DB)\\d*[.](DB)([BWD]\\d*|[X]\\d*[.][0-7])))$")
                .matcher(address);

        return matcher.matches();
    }

    void prepareAddress() {
        try {
            if (address.startsWith("MD")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.M;
                dataType = SimaticPLCDataTypes.DWORD;
            } else if (address.startsWith("MW")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.M;
                dataType = SimaticPLCDataTypes.WORD;
            } else if (address.startsWith("MB")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.M;
                dataType = SimaticPLCDataTypes.BYTE;
            } else if (address.startsWith("M")) {
                String[] items = address.split(".");

                if (items.length != 2) {
                    throw new Exception("No bit area");
                }

                addressByte = Integer.parseInt(items[0].substring(1, items[0].length() - 1));
                addressBit = Integer.parseInt(items[1]);

                items = null;

                area = SimaticPLCAreaTypes.M;
                dataType = SimaticPLCDataTypes.BIT;
            } else if (address.startsWith("ID") || address.startsWith("ED")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.I;
                dataType = SimaticPLCDataTypes.DWORD;
            } else if (address.startsWith("IW") || address.startsWith("EW")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.I;
                dataType = SimaticPLCDataTypes.WORD;
            } else if (address.startsWith("IB") || address.startsWith("EB")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.I;
                dataType = SimaticPLCDataTypes.BYTE;
            } else if (address.startsWith("I") || address.startsWith("E")) {
                String[] items = address.split(".");

                if (items.length != 2) {
                    throw new Exception("No bit area");
                }

                addressByte = Integer.parseInt(items[0].substring(1, items[0].length() - 1));
                addressBit = Integer.parseInt(items[1]);

                items = null;

                area = SimaticPLCAreaTypes.I;
                dataType = SimaticPLCDataTypes.BIT;
            } else if (address.startsWith("QD") || address.startsWith("AD")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.Q;
                dataType = SimaticPLCDataTypes.DWORD;
            } else if (address.startsWith("QW") || address.startsWith("AW")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.Q;
                dataType = SimaticPLCDataTypes.WORD;
            } else if (address.startsWith("QB") || address.startsWith("AB")) {
                addressByte = Integer.parseInt(address.substring(2));
                area = SimaticPLCAreaTypes.Q;
                dataType = SimaticPLCDataTypes.BYTE;
            } else if (address.startsWith("Q") || address.startsWith("A")) {
                String[] items = address.split(".");

                if (items.length != 2) {
                    throw new Exception("No bit area");
                }

                addressByte = Integer.parseInt(items[0].substring(1, items[0].length() - 1));
                addressBit = Integer.parseInt(items[1]);

                items = null;

                area = SimaticPLCAreaTypes.Q;
                dataType = SimaticPLCDataTypes.BIT;
            } else if (address.startsWith("DB")) {
                String[] items = address.split(".");

                if (items.length < 2) {
                    throw new Exception("Short DB address");
                }

                DBNum = Integer.parseInt(items[0].substring(2));
                String tmp = items[1];

                if (tmp.startsWith("DBD")) {
                    addressByte = Integer.parseInt(tmp.substring(3));
                    dataType = SimaticPLCDataTypes.DWORD;
                } else if (tmp.startsWith("DBW")) {
                    addressByte = Integer.parseInt(tmp.substring(3));
                    dataType = SimaticPLCDataTypes.WORD;
                } else if (tmp.startsWith("DBB")) {
                    addressByte = Integer.parseInt(tmp.substring(3));
                    dataType = SimaticPLCDataTypes.WORD;
                } else if (tmp.startsWith("DBX")) {
                    if (items.length < 3) {
                        throw new Exception("No bit area");
                    }

                    addressByte = Integer.parseInt(tmp.substring(3, tmp.length() - 3));
                    addressBit = Integer.parseInt(items[2]);
                    dataType = SimaticPLCDataTypes.BIT;
                } else {
                    area = SimaticPLCAreaTypes.UNKNOWN_AREA;
                    throw new Exception("Invalid area");
                }

                area = SimaticPLCAreaTypes.DB;
            } else {
                area = SimaticPLCAreaTypes.UNKNOWN_AREA;
                throw new Exception("Invalid area");
            }
        } catch (Exception ex) {
            // logger.warn("Invalid address " + address + " (" + ex.getMessage() + ")");
        }
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

}
