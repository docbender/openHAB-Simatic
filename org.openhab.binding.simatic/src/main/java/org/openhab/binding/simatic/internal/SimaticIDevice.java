/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal;

import java.util.Map;

import org.openhab.binding.simatic.internal.SimaticGenericBindingProvider.SimaticBindingConfig;
import org.openhab.binding.simatic.internal.SimaticGenericBindingProvider.SimaticInfoBindingConfig;
import org.openhab.core.events.EventPublisher;
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
     * @param itemName
     *            Item name
     * @param command
     *            Command to send
     * @param config
     *            Item config
     */
    public void sendData(String itemName, Command command, SimaticBindingConfig config);

    /**
     * Check new data for all connected devices
     *
     */
    public void checkNewData();

    /**
     * Method to set binding configuration
     *
     * @param eventPublisher
     * @param itemsConfig
     * @param itemsInfoConfig
     */
    public void setBindingData(EventPublisher eventPublisher, Map<String, SimaticBindingConfig> itemsConfig,
            Map<String, SimaticInfoBindingConfig> itemsInfoConfig);

    /**
     * Method to clear inner binding configuration
     */
    public void unsetBindingData();

    /**
     * Function return device string representation
     */
    @Override
    public String toString();
}
