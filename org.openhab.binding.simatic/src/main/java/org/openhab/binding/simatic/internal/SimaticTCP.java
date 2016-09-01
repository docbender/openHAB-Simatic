/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

import org.openhab.binding.simatic.internal.SimaticGenericBindingProvider.SimaticBindingConfig;
import org.openhab.binding.simatic.internal.SimaticGenericBindingProvider.SimaticInfoBindingConfig;
import org.openhab.binding.simatic.internal.SimaticPortState.PortStates;
import org.openhab.binding.simatic.libnodave.Nodave;
import org.openhab.binding.simatic.libnodave.PLCinterface;
import org.openhab.binding.simatic.libnodave.TCPConnection;
import org.openhab.core.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IP device class
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimaticTCP extends SimaticGenericDevice {

    private static final Logger logger = LoggerFactory.getLogger(SimaticTCP.class);

    /** address */
    private String plcAddress = "";
    /** rack/slot */
    private final int rack, slot;

    Socket sock;
    PLCinterface di;
    TCPConnection dc;
    OutputStream oStream = null;
    InputStream iStream = null;

    /** server socket instance */
    // private AsynchronousServerSocketChannel listener;

    /**
     * Constructor
     *
     * @param deviceName
     * @param ip
     */
    public SimaticTCP(String deviceName, String ip, int rack, int slot) {
        super(deviceName, "isoTCP");

        this.plcAddress = ip;
        this.rack = rack;
        this.slot = slot;
    }

    /**
     * Return bind IP address
     *
     * @return
     */
    protected String getIp() {
        return this.plcAddress;
    }

    @Override
    public void setBindingData(EventPublisher eventPublisher, Map<String, SimaticBindingConfig> itemsConfig,
            Map<String, SimaticInfoBindingConfig> itemsInfoConfig) {
        super.setBindingData(eventPublisher, itemsConfig, itemsInfoConfig);
    }

    /**
     * Check if port is opened
     *
     * @return
     */
    @Override
    public boolean isConnected() {
        return connected;
    }

    /**
     * Open socket
     *
     * @see org.openhab.binding.simplebinary.internal.SimaticIDevice#open()
     */
    @Override
    public Boolean open() {
        if (logger.isDebugEnabled()) {
            logger.debug("{} - connection try", this.toString());
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

        dc = new TCPConnection(di, rack, slot);

        if (dc.connectPLC() == 0) {
            if (logger.isInfoEnabled()) {
                logger.info("{} - connected", this.toString());
            }
            portState.setState(PortStates.LISTENING);
            connected = true;
        } else {
            if (logger.isInfoEnabled()) {
                logger.error("{} - cannot connect to PLC", this.toString());
            }

            return false;
        }

        return true;
    }

    /**
     * Close socket
     *
     * @throws
     *
     *             @see org.openhab.binding.simplebinary.internal.SimaticIDevice#close()
     */
    @Override
    public void close() {

        portState.setState(PortStates.CLOSED);
        connected = false;

        if (dc != null) {
            dc.disconnectPLC();
            dc = null;
        }
        if (di != null) {
            di.disconnectAdapter();
            di = null;
        }
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException e) {
                logger.error("{} - socket close error: {}", this.toString(), e.getMessage());
            } finally {
                sock = null;
                oStream = null;
                iStream = null;
            }
        }

    }

    /**
     * Write data into device stream
     *
     * @param data
     *            Item data with compiled packet
     */
    @Override
    protected boolean sendDataOut(SimaticWriteDataArea data) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} - Sending data to device", this.toString());
        }

        if (data.getAddress().getSimaticDataType() != SimaticPLCDataTypes.BIT) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - writeBytes(area={},db={},adr={},len={}, data={})", this.toString(),
                        data.getAreaIntFormat(), data.getDBNumber(), data.getStartAddress(),
                        data.getAddressSpaceLength(), data.getData().length);

                String datastring = "";
                for (byte b : data.getData()) {
                    datastring += b + ",";
                }

                logger.debug(datastring);
            }

            int result = dc.writeBytes(data.getAreaIntFormat(), data.getDBNumber(), data.getStartAddress(),
                    data.getAddressSpaceLength(), data.getData());
            if (result != 0) {
                logger.error("{} - Write data area error (Area={}, Result=0x{}, Error={})", toString(), data.toString(),
                        Integer.toHexString(result), Nodave.strerror(result));

                if (result == Nodave.RESULT_UNEXPECTED_FUNC) {
                    tryReconnect.set(true);
                }
                return false;
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - writeBits(area={},db={},adr={},len={}, data={})", this.toString(),
                        data.getAreaIntFormat(), data.getDBNumber(),
                        8 * data.getAddress().getByteOffset() + data.getAddress().getBitOffset(),
                        data.getAddressSpaceLength(), data.getData().length);

                String datastring = "";
                for (byte b : data.getData()) {
                    datastring += b + ",";
                }

                logger.debug(datastring);
            }
            int result = dc.writeBits(data.getAreaIntFormat(), data.getDBNumber(),
                    8 * data.getAddress().getByteOffset() + data.getAddress().getBitOffset(),
                    data.getAddressSpaceLength(), data.getData());
            if (result != 0) {
                logger.error("{} - Write data area error (Area={}, Result=0x{}, Error={})", toString(), data.toString(),
                        Integer.toHexString(result), Nodave.strerror(result));

                if (result == Nodave.RESULT_UNEXPECTED_FUNC) {
                    tryReconnect.set(true);
                }
                return false;
            }
        }

        return true;
    }

    /**
     * Check new data for all connected devices
     *
     */
    @Override
    public void checkNewData() {
        if (isConnected()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - checkNewData() is called", toString());
            }

            if (!readLock.tryLock()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} - Reading allready in progress", toString());
                }
                return;
            }

            readLock.lock();

            for (SimaticReadDataArea area : readAreasList.getData()) {
                byte[] buffer = new byte[area.getAddressSpaceLength()];

                int result = dc.readBytes(area.getAreaIntFormat(), area.getDBNumber(), area.getStartAddress(),
                        area.getAddressSpaceLength(), buffer);

                if (result != 0) {
                    logger.error("{} - Read data area error (Area={}, Return code=0x{}, Error={})", toString(),
                            area.toString(), Integer.toHexString(result), Nodave.strerror(result));

                    if (result == Nodave.RESULT_UNEXPECTED_FUNC) {
                        tryReconnect.set(true);
                        break;
                    } else {
                        continue;
                    }
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("{} - Reading finished. Area={}", toString(), area.toString());
                }

                int start = area.getStartAddress();

                for (SimaticBindingConfig item : area.getItems()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("{} - PostValue for item={}", toString(), item.toString());
                    }
                    this.postValue(item, buffer, item.getAddress().getByteOffset() - start);
                }
            }

            readLock.unlock();
        }
    }

    @Override
    public String toString() {
        return deviceID + ":" + getIp();
    }
}
