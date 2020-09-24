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
import java.nio.charset.Charset;

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
     * @param address
     * @param rack
     * @param slot
     * @param pollRate
     * @param charset
     */
    public SimaticTCP(String address, int rack, int slot, int pollRate, Charset charset) {
        super(pollRate, charset);

        this.plcAddress = address;
        this.rack = rack;
        this.slot = slot;

        this.communicationType = 3;
    }

    /**
     * Constructor
     *
     * @param address
     * @param rack
     * @param slot
     * @param communicationType
     * @param pollRate
     * @param charset
     */
    public SimaticTCP(String address, int rack, int slot, String communicationType, int pollRate, Charset charset) {
        super(pollRate, charset);

        this.plcAddress = address;
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
     * Return bind host address
     *
     * @return
     */
    protected String getAddress() {
        return this.plcAddress;
    }

    /**
     * Open socket
     *
     * @see org.openhab.binding.simplebinary.internal.SimaticIDevice#open()
     */
    @Override
    public Boolean open() {
        tryReconnect.set(false);
        if (logger.isDebugEnabled()) {
            logger.debug("{} - open() - connecting...", this.toString());
        }

        portState.setState(PortStates.CLOSED);
        // reset connected state
        setConnected(false);

        // open socket
        try {
            sock = new Socket(this.plcAddress, 102);
            oStream = sock.getOutputStream();
            iStream = sock.getInputStream();
            di = new PLCinterface(oStream, iStream, "IF1", 0, Nodave.PROTOCOL_ISOTCP);
            dc = new TCPConnection(di, rack, slot, communicationType);

            if (dc.connectPLC() == 0) {
                if (logger.isInfoEnabled()) {
                    logger.info("{} - connected. PDU size = {}B", this.toString(), dc.maxPDUlength);
                }
                pduSize = dc.maxPDUlength;
                portState.setState(PortStates.LISTENING);
                // prepare data after PDU is negotiated
                prepareData();
                setConnected(true);
            } else {
                logger.error("{} - cannot connect to PLC", this.toString());
                tryReconnect.set(true);
                return false;
            }
        } catch (Exception ex) {
            logger.error("{} - cannot connect to PLC. {}", this.toString(), ex.getMessage());
            tryReconnect.set(true);
            return false;
        } finally {
            execute();
        }
        return true;
    }

    /**
     * Close socket
     *
     * @see org.openhab.binding.simplebinary.internal.SimaticIDevice#close()
     */
    @Override
    public void close() {
        super.close();

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
        if (!isConnected()) {
            logger.debug("{} - Not connected. Sent discarted.", this.toString());
            return false;
        }

        if (data.getAddress().getSimaticDataType() != SimaticPLCDataTypes.BIT) {
            if (logger.isDebugEnabled()) {
                String datastring = "";
                for (byte b : data.getData()) {
                    datastring += "0x" + Integer.toHexString(b) + ",";
                }

                logger.debug("{} - writeBytes(area={},db={},adr={},len={},datalen={},data={})", this.toString(),
                        data.getAreaIntFormat(), data.getDBNumber(), data.getStartAddress(),
                        data.getAddressSpaceLength(), data.getData().length, datastring);
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
                String datastring = "";
                for (byte b : data.getData()) {
                    datastring += "0x" + Integer.toHexString(b) + ",";
                }

                logger.debug("{} - writeBits(area={},db={},adr={},len={},datalen={},data={})", this.toString(),
                        data.getAreaIntFormat(), data.getDBNumber(),
                        8 * data.getAddress().getByteOffset() + data.getAddress().getBitOffset(),
                        data.getAddressSpaceLength(), data.getData().length, datastring);
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
     * Read data from Simatic area
     *
     * @throws SimaticReadException
     *
     */
    @Override
    public void readDataArea(SimaticReadDataArea area) throws SimaticReadException {
        byte[] buffer = new byte[area.getAddressSpaceLength()];

        int result;
        try {
            result = dc.readBytes(area.getAreaIntFormat(), area.getDBNumber(), area.getStartAddress(),
                    area.getAddressSpaceLength(), buffer);
        } catch (IOException ex) {
            if (isConnected()) {
                portState.setState(PortStates.RESPONSE_ERROR);
                tryReconnect.set(true);
            }
            throw new SimaticReadException(area, ex);
        }

        if (result != 0) {
            String message = String.format("Read data area error (Area=%s, Return code=0x%s, Error=%s})",
                    area.toString(), Integer.toHexString(result), Nodave.strerror(result));
            if (result == Nodave.RESULT_UNEXPECTED_FUNC || result == Nodave.RESULT_READ_DATA_BUFFER_INSUFFICIENT_SPACE
                    || result == Nodave.RESULT_NO_DATA_RETURNED) {
                if (isConnected()) {
                    portState.setState(PortStates.RESPONSE_ERROR);
                    tryReconnect.set(true);
                }
                throw new SimaticReadException(area, message, true);
            } else {

                // update Thing status for all channels in area
                for (SimaticChannel item : area.getItems()) {
                    item.setError(message);
                }
                throw new SimaticReadException(area, message, false);
            }
        }

        int start = area.getStartAddress();

        // get data for all items in area
        for (SimaticChannel item : area.getItems()) {
            // send value into openHAB
            this.postValue(item, buffer, item.getStateAddress().getByteOffset() - start);
        }
    }

    @Override
    public String toString() {
        return getAddress();
    }
}
