/**
 * Copyright (c) 2010-2019, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal.simatic;

import java.io.IOException;
import java.net.Socket;

import org.openhab.binding.simatic.internal.libnodave.Nodave;
import org.openhab.binding.simatic.internal.libnodave.PLCinterface;
import org.openhab.binding.simatic.internal.libnodave.TCP243Connection;
import org.openhab.binding.simatic.internal.simatic.SimaticPortState.PortStates;
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
     * @param address
     * @param rack
     * @param slot
     */
    public SimaticTCP200(String address, int rack, int slot) {
        super(address, rack, slot);
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
        setConnected(false);
        tryReconnect.set(false);

        // open socket
        try {
            sock = new Socket(this.plcAddress, 102);

            oStream = sock.getOutputStream();

            iStream = sock.getInputStream();

            di = new PLCinterface(oStream, iStream, "IF1", 0, Nodave.PROTOCOL_ISOTCP);

            dc = new TCP243Connection(di, rack, slot);

            if (dc.connectPLC() == 0) {
                if (logger.isInfoEnabled()) {
                    logger.info("{} - connected", this.toString());
                }
                portState.setState(PortStates.LISTENING);

                setConnected(true);
            } else {
                logger.error("{} - cannot connect to PLC", this.toString());

                return false;
            }
        } catch (IOException ex) {
            logger.error("{} - cannot connect to PLC due: {}", this.toString(), ex.getMessage());

            return false;
        } finally {
            tryReconnect.set(false);
        }

        return true;
    }
}
