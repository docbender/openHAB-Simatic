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
public class SimaticReadWriteDataArea {

    /** Maximum bytes transfered in one data frame **/
    public static final int MAX_DATA_LENGTH = 192;

    LinkedList<SimaticBindingConfig> items = new LinkedList<SimaticBindingConfig>();
    final SimaticPLCAddress startAddress;

    public SimaticReadWriteDataArea(SimaticBindingConfig firstItem) {
        startAddress = firstItem.getAddress();
        items.add(firstItem);
    }

    public SimaticPLCAreaTypes getArea() {
        return startAddress.getArea();
    }

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

    public int getDBNumber() {
        return startAddress.getDBNumber();
    }

    public int getStartAddress() {
        return startAddress.getByteOffset();
    }

    public int getAddressSpaceLenght() {
        return items.getLast().getAddress().getByteOffset() + items.getLast().getDataLenght()
                - startAddress.getByteOffset();
    }

    public void addItem(SimaticBindingConfig item) throws Exception {
        if (item.getArea() != this.getArea()) {
            throw new Exception("Adding item error. Mismatch area.");
        }

        items.add(item);
    }

    public boolean isItemOutOfRange(SimaticBindingConfig item) {
        return item.getArea() != this.getArea()
                || (this.getArea() == SimaticPLCAreaTypes.DB && (startAddress.DBNum != item.getAddress().DBNum))
                || (item.getAddress().addressByte - this.startAddress.addressByte
                        + this.startAddress.getDataLength() > MAX_DATA_LENGTH);
    }

    @Override
    public String toString() {
        if (getArea() == SimaticPLCAreaTypes.DB) {
            return "DB" + getDBNumber() + ".DBB" + getStartAddress() + "-DB" + getDBNumber() + ".DBB"
                    + items.getLast().getAddress().getByteOffset() + items.getLast().getDataLenght();
        } else {
            return getArea().toString() + getStartAddress() + "-" + getArea().toString()
                    + items.getLast().getAddress().getByteOffset() + items.getLast().getDataLenght();
        }
    }

    public LinkedList<SimaticBindingConfig> getItems() {
        return items;
    }
}
