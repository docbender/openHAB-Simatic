/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.simatic.internal.config;

import org.openhab.binding.simatic.internal.simatic.SimaticUpdateMode;

/**
 * The {@link SimaticBridgeConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author VitaTucek - Initial contribution
 */
public class SimaticBridgeConfiguration {

    /**
     * Device IP/Host address
     */
    public String address;

    /**
     * CPU Rack number
     */
    public int rack = 0;

    /**
     * CPU Slot number
     */
    public int slot = 2;

    /**
     * Communication type (PG,OP,S7)
     */
    public String communicationType = "S7";

    /**
     * Is device S7-200 PLC (CP242)
     */
    public boolean isS7200 = false;

    /**
     * String data code page
     */
    public String charset = "";

    /**
     * Device poll rate
     */
    public int pollRate = 1000;

    /**
     * Value update mode (OC,PL)
     */
    public String updateMode = "OnChange";

    /**
     * Get Value Update Mode
     *
     * @return Return Update mode
     */
    public SimaticUpdateMode getUpdateMode() {
        return SimaticUpdateMode.valueOf(updateMode);
    }

}
