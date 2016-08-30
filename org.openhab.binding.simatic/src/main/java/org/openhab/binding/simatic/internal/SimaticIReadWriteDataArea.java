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
 * @since 1.9.0
 */
public interface SimaticIReadWriteDataArea {
    /** Maximum bytes transfered in one data frame **/
    public static final int MAX_DATA_LENGTH = 192;

    public SimaticPLCAreaTypes getArea();

    public int getAreaIntFormat();

    public int getDBNumber();

    public int getStartAddress();

    public int getAddressSpaceLength();

    public boolean isItemOutOfRange(SimaticPLCAddress item);
}
