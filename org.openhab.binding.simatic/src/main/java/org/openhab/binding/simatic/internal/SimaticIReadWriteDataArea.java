/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal;

/**
 * Read / write area interface
 *
 * @author Vita Tucek
 * @since 1.14.0
 */
public interface SimaticIReadWriteDataArea {
    /** Maximum bytes transfered in one data frame **/
    public static final int MAX_DATA_LENGTH = 192;
    public static final int MAX_PDU480_DATA_LENGTH = 462;

    /**
     * Return PLC area type
     *
     * @return
     */
    public SimaticPLCAreaTypes getArea();

    /**
     * Return area integer representation
     *
     * @return
     */
    public int getAreaIntFormat();

    /**
     * Return datablock number
     *
     * @return
     */
    public int getDBNumber();

    /**
     * Return area start (first item) address
     *
     * @return
     */
    public int getStartAddress();

    /**
     * Return area length [bytes]
     *
     * @return
     */
    public int getAddressSpaceLength();

    /**
     * Check if item can't be part of area
     *
     * @param item
     * @return True if item can't be in this area False otherwise
     */
    public boolean isItemOutOfRange(SimaticPLCAddress item);
}
