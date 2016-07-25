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
 *
 * Class holding item data
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimaticItemData {

    protected byte[] itemData;

    /**
     * Construct item data instance for unspecified item
     *
     * @param itemData
     *            Raw data
     */
    public SimaticItemData(byte[] itemData) {

        this.itemData = itemData;
    }

    /**
     * Return item raw data
     *
     * @return
     */
    public byte[] getData() {
        return itemData;
    }
}
