/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal;

import java.util.LinkedList;

import org.openhab.binding.simatic.internal.SimaticGenericBindingProvider.SimaticBindingConfig;
import org.openhab.binding.simatic.libnodave.Nodave;

/**
 * Read / write area class
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimaticReadDataArea implements SimaticIReadWriteDataArea {
    /** Maximum space between two useful data block **/
    public static final int GAP_LIMIT = 32;

    LinkedList<SimaticBindingConfig> items = new LinkedList<SimaticBindingConfig>();
    final SimaticPLCAddress startAddress;

    public SimaticReadDataArea(SimaticBindingConfig firstItem) {
        startAddress = firstItem.getAddress();
        items.add(firstItem);
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
        return items.getLast().getAddress().getByteOffset() + items.getLast().getDataLength()
                - startAddress.getByteOffset();
    }

    public void addItem(SimaticBindingConfig item) throws Exception {
        if (item.getArea() != this.getArea()) {
            throw new Exception("Adding item error. Mismatch area.");
        }

        items.add(item);
    }

    @Override
    public boolean isItemOutOfRange(SimaticPLCAddress itemAddress) {
        // must be in area, eventually same DB, in range of maximal frame size and in defined gap space
        return itemAddress.getArea() != this.getArea()
                || (this.getArea() == SimaticPLCAreaTypes.DB && (startAddress.DBNum != itemAddress.DBNum))
                || (itemAddress.addressByte + itemAddress.getDataLength()
                        - this.startAddress.addressByte > MAX_DATA_LENGTH)
                || (itemAddress.addressByte
                        - (this.startAddress.addressByte + this.getAddressSpaceLength()) > GAP_LIMIT);
    }

    @Override
    public String toString() {
        if (getArea() == SimaticPLCAreaTypes.DB) {
            return "DB" + getDBNumber() + ".DBB" + getStartAddress() + "-DB" + getDBNumber() + ".DBB"
                    + items.getLast().getAddress().getByteOffset() + items.getLast().getDataLength();
        } else {
            return getArea().toString() + getStartAddress() + "-" + getArea().toString()
                    + items.getLast().getAddress().getByteOffset() + items.getLast().getDataLength();
        }
    }

    public LinkedList<SimaticBindingConfig> getItems() {
        return items;
    }
}
