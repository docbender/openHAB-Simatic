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

/**
 * The {@link SimaticBridgeConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author VitaTucek - Initial contribution
 */
public class SimaticBridgeConfiguration {

    /**
     * Device IP address
     */
    public String ipAddress;

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
}
