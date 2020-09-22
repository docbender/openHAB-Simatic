/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal.simatic;

import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.core.types.Command;

/**
 * Device interface
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public interface SimaticIDevice {
    public interface ConnectionChanged {
        public void onConnectionChanged(boolean connected);
    }

    public interface MetricsUpdated {
        public void onMetricsUpdated(long requests, long bytes);
    }

    /**
     * Release resources
     *
     */
    void dispose();

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
     * Set read write areas
     *
     */
    public void setDataAreas(@NonNull ArrayList<@NonNull SimaticChannel> stateItems);

    /**
     * Function return device string representation
     */
    @Override
    public String toString();

    /**
     * Set method provided on connection changes
     */
    public void onConnectionChanged(ConnectionChanged onChangeMethod);

    /**
     * Set method provided on update metrics
     */
    public void onMetricsUpdated(MetricsUpdated onUpdateMethod);

    /**
     * Check new data for all connected devices
     *
     * @throws SimaticReadException
     *
     */
    void readDataArea(SimaticReadDataArea area) throws SimaticReadException;
}
