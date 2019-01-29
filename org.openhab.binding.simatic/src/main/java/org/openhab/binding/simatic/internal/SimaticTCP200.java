/**
 * Copyright (c) 2010-2019, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal;

import java.io.IOException;
import java.net.Socket;

import org.openhab.binding.simatic.internal.SimaticPortState.PortStates;
import org.openhab.binding.simatic.libnodave.Nodave;
import org.openhab.binding.simatic.libnodave.PLCinterface;
import org.openhab.binding.simatic.libnodave.TCP243Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IP CP243 device class
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimaticTCP200 extends SimaticTCP {

    private static final Logger logger = LoggerFactory.getLogger(SimaticTCP.class);

    /**
     * Constructor
     *
     * @param deviceName
     * @param ip
     * @param rack
     * @param slot
     */
    public SimaticTCP200(String deviceName, String ip, int rack, int slot) {
        super(deviceName, ip, rack, slot);
        // TODO Auto-generated constructor stub
    }

    /**
     * Open socket
     *
     * @see org.openhab.binding.simplebinary.internal.SimaticIDevice#open()
     */
    @Override
    public Boolean open() {
        if (logger.isDebugEnabled()) {
            logger.debug("{} - open() - connecting CP243", this.toString());
        }

        portState.setState(PortStates.CLOSED);
        // reset connected state
        connected = false;

        // open socket
        try {
            sock = new Socket(this.plcAddress, 102);
        } catch (IOException e) {
            logger.error("{} - create socket error: {}", this.toString(), e.getMessage());
            return false;
        }

        if (sock == null) {
            logger.error("{} - socket was not created (null returned)", this.toString());
            return false;
        }

        try {
            oStream = sock.getOutputStream();
        } catch (IOException e) {
            logger.error("{} - getOutputStream error: {}", this.toString(), e.getMessage());
            return false;
        }
        try {
            iStream = sock.getInputStream();
        } catch (IOException e) {
            logger.error("{} - getInputStream error: {}", this.toString(), e.getMessage());
            return false;
        }
        di = new PLCinterface(oStream, iStream, "IF1", 0, Nodave.PROTOCOL_ISOTCP);

        dc = new TCP243Connection(di, rack, slot);

        try {
            if (dc.connectPLC() == 0) {
                if (logger.isInfoEnabled()) {
                    logger.info("{} - connected", this.toString());
                }
                portState.setState(PortStates.LISTENING);
                tryReconnect.set(false);
                connected = true;
            } else {
                logger.error("{} - cannot connect to PLC", this.toString());

                return false;
            }
        } catch (IOException ex) {
            logger.error("{} - cannot connect to PLC due: {}", this.toString(), ex.getMessage());

            return false;
        }

        return true;
    }
}
