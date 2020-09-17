/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal.simatic;

import java.util.LinkedList;

import org.openhab.binding.simatic.internal.libnodave.Nodave;

/**
 * Read / write area class
 *
 * @author Vita Tucek
 * @since 1.14.0
 */
public class SimaticReadDataArea implements SimaticIReadWriteDataArea {
    /** Maximum space between two useful data block **/
    public static final int GAP_LIMIT = 32;

    final LinkedList<SimaticChannel> items = new LinkedList<SimaticChannel>();
    final SimaticPLCAddress startAddress;
    int areaLength = 0;
    /** data limit PDU size depending **/
    int dataLimit = MAX_DATA_LENGTH;

    public SimaticReadDataArea(SimaticChannel firstItem, int pduSize) {
        startAddress = firstItem.getStateAddress();
        items.add(firstItem);

        areaLength = startAddress.getDataLength();
        if (pduSize > READ_OVERHEAD) {
            dataLimit = pduSize - READ_OVERHEAD;
        }
    }

    @Override
    public SimaticPLCAreaTypes getArea() {
        return startAddress.getArea();
    }

    @Override
    public int getAreaIntFormat() {
        switch (startAddress.getArea()) {
            case I:
                return Nodave.INPUTS;
            case Q:
                return Nodave.OUTPUTS;
            case DB:
                return Nodave.DB;
            case M:
                return Nodave.FLAGS;
            default:
                break;
        }

        return -1;
    }

    @Override
    public int getDBNumber() {
        return startAddress.getDBNumber();
    }

    @Override
    public int getStartAddress() {
        return startAddress.getByteOffset();
    }

    @Override
    public int getAddressSpaceLength() {
        return areaLength;
    }

    private int getEndByteOffset() {
        return startAddress.getByteOffset() + areaLength;
    }

    public void addItem(SimaticChannel item) throws Exception {
        if (item.getStateAddress().getArea() != this.getArea()) {
            throw new Exception("Adding item error. Mismatch area.");
        }

        items.add(item);

        int endAddress = startAddress.getByteOffset();

        for (SimaticChannel i : items) {
            int itemEnd = i.getStateAddress().getByteOffset() + i.getStateAddress().getDataLength();
            if (itemEnd > endAddress) {
                endAddress = itemEnd;
            }
        }

        areaLength = endAddress - startAddress.getByteOffset();
    }

    @Override
    public boolean isItemOutOfRange(SimaticPLCAddress itemAddress) {
        // Logger logger = LoggerFactory.getLogger(SimaticPLCAddress.class);
        //
        // logger.info("This address: {}------------------", this.startAddress.toString());
        // logger.info("New address: {}------------------", itemAddress.toString());
        //
        // logger.debug("Area{}/{} = {}", itemAddress.getArea(), this.getArea(), itemAddress.getArea() !=
        // this.getArea());
        // logger.debug("DB{}/{} = {}", startAddress.DBNum, itemAddress.DBNum,
        // (this.getArea() == SimaticPLCAreaTypes.DB && !startAddress.DBNum.equals(itemAddress.DBNum)));
        // logger.debug("Address{}/{} = {}", (itemAddress.addressByte + itemAddress.getDataLength()),
        // this.startAddress.addressByte, (itemAddress.addressByte + itemAddress.getDataLength()
        // - this.startAddress.addressByte > MAX_DATA_LENGTH));
        // logger.debug("Gap{}/{} = {}", itemAddress.addressByte,
        // (this.startAddress.addressByte + this.getAddressSpaceLength()),
        // (itemAddress.addressByte - (this.startAddress.addressByte + this.getAddressSpaceLength()) > GAP_LIMIT));

        // must be in area, eventually same DB, in range of maximal frame size and in defined gap space
        return itemAddress.getArea() != this.getArea()
                || (this.getArea() == SimaticPLCAreaTypes.DB && !startAddress.dBNum.equals(itemAddress.dBNum))
                || (itemAddress.addressByte + itemAddress.getDataLength() - this.startAddress.addressByte > dataLimit)
                || (itemAddress.addressByte
                        - (this.startAddress.addressByte + this.getAddressSpaceLength()) > GAP_LIMIT);
    }

    @Override
    public String toString() {
        if (getArea() == SimaticPLCAreaTypes.DB) {
            return "DB" + getDBNumber() + ".DBB" + getStartAddress() + "-DB" + getDBNumber() + ".DBB"
                    + getEndByteOffset();
        } else {
            return getArea().toString() + getStartAddress() + "-" + getArea().toString() + getEndByteOffset();
        }
    }

    public LinkedList<SimaticChannel> getItems() {
        return items;
    }
}
