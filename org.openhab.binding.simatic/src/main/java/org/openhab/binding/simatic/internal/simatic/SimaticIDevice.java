/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal.simatic;

import java.util.List;

import org.openhab.core.types.Command;

/**
 * Device interface
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public interface SimaticIDevice {
    /**
     * Open device connection
     *
     * @return
     */
    public Boolean open();

    /**
     * Close device connection
     *
     */
    public void close();

    /**
     * Send data to device
     *
     * @param item
     *            Channel data
     * @param command
     *            Command to send
     */
    public void sendData(SimaticChannel item, Command command);

    /**
     * Check new data for all connected devices
     *
     */
    public void checkNewData();

    /**
     * Set read write areas
     *
     */
    public void setDataAreas(List<SimaticChannel> stateItems);

    /**
     * Function return device string representation
     */
    @Override
    public String toString();
}
