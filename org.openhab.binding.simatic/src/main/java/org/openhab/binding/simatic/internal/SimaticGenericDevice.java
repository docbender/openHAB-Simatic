/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.openhab.binding.simatic.internal.SimaticGenericBindingProvider.SimaticBindingConfig;
import org.openhab.binding.simatic.internal.SimaticGenericBindingProvider.SimaticInfoBindingConfig;
import org.openhab.binding.simatic.internal.SimaticPortState.PortStates;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.library.items.ColorItem;
import org.openhab.core.library.items.ContactItem;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.RollershutterItem;
import org.openhab.core.library.items.StringItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic device class
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimaticGenericDevice implements SimaticIDevice {
    private static final Logger logger = LoggerFactory.getLogger(SimaticGenericDevice.class);

    /** device name ex.: plc,plc1, ... */
    protected final String deviceName;
    /** device ID ex.: 192.168.1.1, ... */
    protected final String deviceID;
    /** defines maximum resend count */
    public final int MAX_RESEND_COUNT = 2;

    protected EventPublisher eventPublisher;
    /** item config */
    protected Map<String, SimaticBindingConfig> itemsConfig;

    /** flag that device is connected */
    protected boolean connected = false;
    /** queue for commands */
    protected final Deque<SimaticWriteDataArea> commandQueue = new LinkedList<SimaticWriteDataArea>();
    /** State of socket */
    public SimaticPortState portState = new SimaticPortState();
    /** Lock for process commands to prevent run it twice **/
    protected final Lock lock = new ReentrantLock();
    protected final Lock readLock = new ReentrantLock();
    /** Read PLC areas **/
    protected SimaticReadQueue readAreasList = new SimaticReadQueue();

    public enum ProcessDataResult {
        OK,
        DATA_NOT_COMPLETED,
        PROCESSING_ERROR,
        INVALID_CRC,
        BAD_CONFIG,
        NO_VALID_ADDRESS,
        NO_VALID_ADDRESS_REWIND,
        UNKNOWN_MESSAGE,
        UNKNOWN_MESSAGE_REWIND
    }

    /**
     * Constructor
     *
     * @param deviceName
     * @param deviceID
     */
    public SimaticGenericDevice(String deviceName, String deviceID) {
        this.deviceName = deviceName;
        this.deviceID = deviceID;
    }

    /**
     * Method to set binding configuration
     *
     * @param eventPublisher
     * @param itemsConfig
     * @param itemsInfoConfig
     */
    @Override
    public void setBindingData(EventPublisher eventPublisher, Map<String, SimaticBindingConfig> itemsConfig,
            Map<String, SimaticInfoBindingConfig> itemsInfoConfig) {
        this.eventPublisher = eventPublisher;
        this.itemsConfig = itemsConfig;

        this.portState.setBindingData(eventPublisher, itemsInfoConfig, this.deviceName);
    }

    /**
     * Method to clear inner binding configuration
     */
    @Override
    public void unsetBindingData() {
        this.eventPublisher = null;
        this.itemsConfig = null;
    }

    /**
     * Check if port is opened
     *
     * @return
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Open
     *
     * @see org.openhab.binding.SimaticIDevice.internal.SimaticIDevice#open()
     */
    @Override
    public Boolean open() {
        logger.warn("{} - Opening... cannot open generic device", toString());

        return false;
    }

    /**
     * Close
     *
     * @see org.openhab.binding.SimaticIDevice.internal.SimaticIDevice#close()
     */
    @Override
    public void close() {
        logger.warn("{} - Closing... cannot close generic device", toString());

        connected = false;
    }

    /**
     * Reconnect device
     */
    private void reconnect() {
        logger.info("{}: Trying to reconnect", toString());

        close();
        open();
    }

    /**
     * Send command into device channel
     *
     * @see org.openhab.binding.SimaticIDevice.internal.SimaticIDevice#sendData(java.lang.String,
     *      org.openhab.core.types.Command,
     *      org.openhab.binding.simplebinary.internal.SimaticGenericBindingProvider.SimaticBindingConfig)
     */
    @Override
    public void sendData(String itemName, Command command, SimaticBindingConfig config) {

        sendData(SimaticWriteDataArea.create(command, config));
    }

    /**
     * Add compiled data item to sending queue
     *
     * @param data
     */
    public void sendData(SimaticWriteDataArea data) {
        if (data != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("{}: Adding command into queue", toString());
            }

            // lock queue
            lock.lock();

            if (commandQueue.size() == 0) {
                // add data
                commandQueue.addFirst(data);
            } else {
                for (SimaticWriteDataArea item : commandQueue) {
                    if (!item.isItemOutOfRange(data.getAddress())) {
                        item.insert(data);

                        break;
                    }
                }
            }
            // unlock queue
            lock.unlock();

            processCommandQueue();
        } else {
            logger.warn("{}: Nothing to send. Empty data", toString());
        }
    }

    /**
     * Check if queue is not empty and send data to device
     *
     */
    protected void processCommandQueue() {
        if (logger.isDebugEnabled()) {
            logger.debug("{} - Processing commandQueue - length {}. Thread={}", toString(), commandQueue.size(),
                    Thread.currentThread().getId());
        }

        // no reply expected
        if (!canSend()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - Processing commandQueue - waiting", this.toString());
            }
            return;
        }

        if (!lock.tryLock()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - CommandQueue locked. Leaving processCommandQueue.", toString());
            }
            return;
        }

        SimaticWriteDataArea dataToSend = null;

        try {
            // queue is empty -> exit
            if (commandQueue.isEmpty()) {
                return;
            }

            // check if device responds and there is lot of commands
            if (this.portState.getState() != PortStates.NOT_RESPONDING) {
                dataToSend = commandQueue.poll();
            }
        } catch (Exception e) {

        } finally {
            lock.unlock();
        }

        if (dataToSend != null) {
            sendDataOut(dataToSend);
        }
    }

    protected boolean canSend() {
        return this.isConnected();
    }

    /**
     * Write data into device stream
     *
     * @param data
     *            Item data with compiled packet
     * @return
     *         Return true when data were sent
     */
    protected boolean sendDataOut(SimaticWriteDataArea data) {

        logger.warn("{} - Generic device cant send data", this.toString());

        return false;
    }

    /**
     * @see org.openhab.binding.SimaticIDevice.internal.SimaticIDevice#checkNewData()
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

            for (SimaticReadDataArea item : readAreasList.getData()) {
                // Read data depend on connection type
            }

            readLock.unlock();
        }
    }

    @Override
    public String toString() {
        return "DeviceID " + deviceID;
    }

    public void prepareData() {
        if (itemsConfig == null) {
            return;
        }

        // sort items by address
        List<Map.Entry<String, SimaticBindingConfig>> list = new LinkedList<Map.Entry<String, SimaticBindingConfig>>(
                itemsConfig.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<String, SimaticBindingConfig>>() {
            @Override
            public int compare(Map.Entry<String, SimaticBindingConfig> o1, Map.Entry<String, SimaticBindingConfig> o2) {
                return (o1.getValue().address).compareTo(o2.getValue().address);
            }
        });

        SimaticReadDataArea readDataArea = null;

        // prepare read queues
        for (Map.Entry<String, SimaticBindingConfig> item : list) {
            // no data with output direction
            if (item.getValue().direction == 2) {
                continue;
            }

            if (readDataArea == null || readDataArea.isItemOutOfRange(item.getValue().getAddress())) {
                readDataArea = new SimaticReadDataArea(item.getValue());
                readAreasList.put(readDataArea);
            } else {
                try {
                    readDataArea.addItem(item.getValue());
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
        }

        logger.debug("readAreas:");

        for (SimaticReadDataArea i : readAreasList.getData()) {
            logger.debug(i.toString());
        }
    }

    public void postValue(SimaticBindingConfig item, byte[] buffer, int position) {
        // logger.debug("item={}", item.toString());
        // logger.debug("buffer={}", buffer.length);
        // logger.debug("position={}", position);
        // logger.debug("item len={}", item.getDataLength());

        ByteBuffer bb = ByteBuffer.wrap(buffer, position, item.getDataLength());
        State state = null;
        Class<?> itemclass = item.getOpenHabItem().getClass();

        // logger.info("postvalue Class=" + itemclass.toString());

        // no byte swap for array
        // if (item.datatype == SimaticTypes.ARRAY) {
        bb.order(ByteOrder.BIG_ENDIAN);
        // } else {
        // bb.order(ByteOrder.LITTLE_ENDIAN);
        // }

        if (item.datatype == SimaticTypes.ARRAY) {
            if (itemclass.isAssignableFrom(StringItem.class)) {
                String str = new String(buffer, position, item.getDataLength());
                state = new StringType(str);
            } else {
                logger.warn("{} - Incoming data item {} - Array is only supported for string item.", toString(),
                        item.getName());
            }
        } else {

            if (!itemclass.isAssignableFrom(SwitchItem.class) && itemclass.isAssignableFrom(ColorItem.class)) {
                if (item.address.dataType != SimaticPLCDataTypes.DWORD) {
                    logger.warn("{} - Incoming data item {} - Color item must have DWORD address", toString(),
                            item.getName());
                } else {
                    byte b0 = bb.get();
                    byte b1 = bb.get();
                    byte b2 = bb.get();

                    if (item.getDataType() == SimaticTypes.HSB) {
                        state = new HSBType(new DecimalType(b0), new PercentType(b1), new PercentType(b2));
                    } else if (item.getDataType() == SimaticTypes.RGB) {
                        state = new HSBType(new Color(b0, b1, b2));
                    } else if (item.getDataType() == SimaticTypes.RGBW) {
                        state = new HSBType(new Color(b0 & 0xFF, b1 & 0xFF, b2 & 0xFF));
                    } else {
                        logger.warn("{} - Incoming data item {} - Unsupported color type {}.", toString(),
                                item.getName(), item.getDataType());
                    }
                }
            } else {
                if ((item.datatype == SimaticTypes.FLOAT) && itemclass.isAssignableFrom(NumberItem.class)) {
                    if (item.address.dataType == SimaticPLCDataTypes.DWORD) {
                        state = new DecimalType(bb.getFloat());
                    } else {
                        logger.warn("{} - Incoming data item {} - Float is only supported with DWORD address.",
                                toString(), item.getName());
                    }
                } else {
                    int intValue = 0;

                    if (item.address.dataType == SimaticPLCDataTypes.BIT) {
                        intValue = (bb.get() & (int) Math.pow(2, item.getAddress().getBitOffset())) != 0 ? 1 : 0;
                    } else if (item.address.dataType == SimaticPLCDataTypes.BYTE) {
                        intValue = bb.get();
                    } else if (item.address.dataType == SimaticPLCDataTypes.WORD) {
                        intValue = bb.getShort();
                    } else if (item.address.dataType == SimaticPLCDataTypes.DWORD) {
                        intValue = bb.getInt();
                    }

                    if (itemclass.isAssignableFrom(NumberItem.class)) {
                        state = new DecimalType(intValue);
                    } else if (itemclass.isAssignableFrom(SwitchItem.class)) {
                        if (intValue == 1) {
                            state = OnOffType.ON;
                        } else {
                            state = OnOffType.OFF;
                        }
                    } else if (itemclass.isAssignableFrom(DimmerItem.class)) {
                        state = new PercentType(intValue);
                    } else if (itemclass.isAssignableFrom(ContactItem.class)) {
                        if (intValue == 1) {
                            state = OpenClosedType.OPEN;
                        } else {
                            state = OpenClosedType.CLOSED;
                        }
                    } else if (itemclass.isAssignableFrom(RollershutterItem.class)) {
                        state = new PercentType(intValue);
                    } else {
                        logger.warn("{} - Incoming data item {} - Class {} is not supported.", toString(),
                                item.getName(), itemclass.toString());
                    }
                }
            }
        }

        postState(item.getName(), state);
    }

    public void postState(String itemName, State state) {
        if (state == null) {
            logger.warn("{} - Incoming data item {} - Unknown  state", toString(), itemName);
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - Incoming data - item:{}/state:{}", toString(), itemName, state);
            }

            if (eventPublisher != null) {
                eventPublisher.postUpdate(itemName, state);
            }
        }
    }
}
