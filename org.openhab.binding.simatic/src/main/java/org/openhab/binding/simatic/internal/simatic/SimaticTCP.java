/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal.simatic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.openhab.binding.simatic.internal.libnodave.Nodave;
import org.openhab.binding.simatic.internal.libnodave.PLCinterface;
import org.openhab.binding.simatic.internal.libnodave.TCPConnection;
import org.openhab.binding.simatic.internal.simatic.SimaticPortState.PortStates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IP device class
 *
 * @author Vita Tucek
 * @since 1.14.0
 */
public class SimaticTCP extends SimaticGenericDevice {

    private static final Logger logger = LoggerFactory.getLogger(SimaticTCP.class);

    /** address */
    protected String plcAddress = "";
    /** rack/slot */
    protected final int rack, slot, communicationType;

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
     * @param ip
     * @param rack
     * @param slot
     */
    public SimaticTCP(String ip, int rack, int slot) {
        super();

        this.plcAddress = ip;
        this.rack = rack;
        this.slot = slot;

        this.communicationType = 3;
    }

    /**
     * Constructor
     *
     * @param ip
     * @param rack
     * @param slot
     * @param communicationType
     */
    public SimaticTCP(String ip, int rack, int slot, String communicationType) {
        super();

        this.plcAddress = ip;
        this.rack = rack;
        this.slot = slot;

        if (communicationType.equals("PG")) {
            this.communicationType = 1;
        } else if (communicationType.equals("OP")) {
            this.communicationType = 2;
        } else {
            this.communicationType = 3;
        }
    }

    /**
     * Return bind IP address
     *
     * @return
     */
    protected String getIp() {
        return this.plcAddress;
    }

    /**
     * Open socket
     *
     * @see org.openhab.binding.simplebinary.internal.SimaticIDevice#open()
     */
    @Override
    public Boolean open() {
        if (logger.isDebugEnabled()) {
            logger.debug("{} - open() - connecting", this.toString());
        }

        portState.setState(PortStates.CLOSED);
        // reset connected state
        setConnected(false);

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

        dc = new TCPConnection(di, rack, slot, communicationType);

        try {
            if (dc.connectPLC() == 0) {
                if (logger.isInfoEnabled()) {
                    logger.info("{} - connected. PDU size = {}B", this.toString(), dc.maxPDUlength);
                }
                pduSize = dc.maxPDUlength;
                portState.setState(PortStates.LISTENING);
                tryReconnect.set(false);
                // prepare data after PDU is negotiated
                prepareData();
                setConnected(true);
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

    /**
     * Close socket
     *
     * @throws
     *
     * @see    org.openhab.binding.simplebinary.internal.SimaticIDevice#close()
     */
    @Override
    public void close() {
        if (logger.isDebugEnabled()) {
            logger.debug("{} - close() - disconnecting", this.toString());
        }
        portState.setState(PortStates.CLOSED);
        setConnected(false);

        if (dc != null) {
            try {
                dc.disconnectPLC();
            } catch (IOException ex) {

            }
            dc = null;
        }
        if (di != null) {
            try {
                di.disconnectAdapter();
            } catch (IOException ex) {

            }
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
        // TODO: If the connection is reset because of a network error, we can try to re-send the write instead of
        // dropping it. -- AchilleGR
        // TODO: Don't allow writing to addresses corresponding to items marked as read only -- AchilleGR
        if (logger.isDebugEnabled()) {
            logger.debug("{} - Sending data to device", this.toString());
        }

        if (!isConnected()) {
            logger.debug("{} - Not connected. Sent discarted.", this.toString());
            return false;
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

            try {
                int result = dc.writeBytes(data.getAreaIntFormat(), data.getDBNumber(), data.getStartAddress(),
                        data.getAddressSpaceLength(), data.getData());
                if (result != 0) {
                    logger.error("{} - Write data area error (Area={}, Result=0x{}, Error={})", toString(),
                            data.toString(), Integer.toHexString(result), Nodave.strerror(result));

                    if (result == Nodave.RESULT_UNEXPECTED_FUNC) {
                        tryReconnect.set(true);
                    }
                    return false;
                }
            } catch (IOException ex) {
                logger.error("{} - Write data area error (Area={}, Error={})", toString(), data.toString(),
                        ex.getMessage());
                portState.setState(PortStates.RESPONSE_ERROR);
                tryReconnect.set(true);

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
            int result;
            try {
                result = dc.writeBits(data.getAreaIntFormat(), data.getDBNumber(),
                        8 * data.getAddress().getByteOffset() + data.getAddress().getBitOffset(),
                        data.getAddressSpaceLength(), data.getData());
            } catch (IOException ex) {
                logger.error("{} - Write data area error (Area={}, Error={})", toString(), data.toString(),
                        ex.getMessage());
                portState.setState(PortStates.RESPONSE_ERROR);
                tryReconnect.set(true);

                return false;
            }

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
        if (!isConnected()) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("{} - checkNewData() is called", toString());
        }

        if (!readLock.tryLock()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - Reading already in progress", toString());
            }
            return;
        }

        logger.debug("{} - Locking", toString());

        try {
            for (SimaticReadDataArea area : readAreasList.getData()) {
                byte[] buffer = new byte[area.getAddressSpaceLength()];

                int result;
                try {
                    result = dc.readBytes(area.getAreaIntFormat(), area.getDBNumber(), area.getStartAddress(),
                            area.getAddressSpaceLength(), buffer);
                } catch (IOException ex) {
                    logger.error("{} - Read data area error (Area={}, Error={})", toString(), area.toString(),
                            ex.getMessage());

                    if (isConnected()) {
                        portState.setState(PortStates.RESPONSE_ERROR);
                        tryReconnect.set(true);
                    }
                    return;
                }

                if (result != 0) {

                    logger.error("{} - Read data area error (Area={}, Return code=0x{}, Error={})", toString(),
                            area.toString(), Integer.toHexString(result), Nodave.strerror(result));

                    if (result == Nodave.RESULT_UNEXPECTED_FUNC
                            || result == Nodave.RESULT_READ_DATA_BUFFER_INSUFFICIENT_SPACE
                            || result == Nodave.RESULT_NO_DATA_RETURNED) {
                        if (isConnected()) {
                            portState.setState(PortStates.RESPONSE_ERROR);
                            tryReconnect.set(true);
                        }
                        return;
                    } else {
                        String message = String.format(
                                "%s - Read data area error (Area=%s, Return code=0x%s, Error=%s})", toString(),
                                area.toString(), Integer.toHexString(result), Nodave.strerror(result));
                        // update Thing status for all channels in area
                        for (SimaticChannel item : area.getItems()) {
                            item.setError(message);
                        }
                        continue;
                    }
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("{} - Reading finished. Area={}", toString(), area.toString());
                }

                int start = area.getStartAddress();

                // get data for all items in area
                for (SimaticChannel item : area.getItems()) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("{} - PostValue for item={}", toString(), item.toString());
                    }
                    // send value into openHAB
                    this.postValue(item, buffer, item.getStateAddress().getByteOffset() - start);
                }
            }
        } catch (Exception ex) {
            logger.error("{} - Read data error", toString(), ex);
        } finally {
            logger.debug("{} - Unlocking", toString());
            readLock.unlock();
        }
    }

    @Override
    public String toString() {
        return getIp();
    }
}
