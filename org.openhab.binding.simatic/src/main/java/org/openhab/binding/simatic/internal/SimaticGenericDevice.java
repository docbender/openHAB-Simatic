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
    protected final Deque<SimaticItemData> commandQueue = new LinkedList<SimaticItemData>();
    /** State of socket */
    public SimaticPortState portState = new SimaticPortState();
    /** Lock for process commands to prevent run it twice **/
    protected final Lock lock = new ReentrantLock();
    protected final Lock readLock = new ReentrantLock();
    /** Read PLC areas **/
    protected SimaticReadWriteQueue readAreasList = new SimaticReadWriteQueue();

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
        // compile data
        // SimaticItem data = SimaticProtocol.compileDataFrame(itemName, command, config);

        // sendData(data);
    }

    /**
     * Add compiled data item to sending queue
     *
     * @param data
     */
    public void sendData(SimaticItemData data) {
        if (data != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("{}: Adding command into queue", toString());
            }

            // lock queue
            lock.lock();
            // add data
            commandQueue.offer(data);
            // unlock queue
            lock.unlock();

            processCommandQueue();
        } else {
            logger.warn("{}: Nothing to send. Empty data", toString());
        }
    }

    /**
     * Add compiled data item to sending queue at first place
     *
     * @param data
     */
    public void sendDataPriority(SimaticItemData data) {
        if (data != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("{}: Adding priority command into queue", toString());
            }

            // lock queue
            lock.lock();
            // add data
            commandQueue.addFirst(data);
            // unlock queue
            lock.unlock();

            processCommandQueue();
        } else {
            logger.warn("{}: Nothing to send. Empty data", toString());
        }
    }

    //
    // /**
    // * Prepare request to check if device with specific address has new data
    // *
    // * @param deviceAddress Device address
    // * @param forceAllDataAsNew Flag to force send all data from slave
    // */
    // protected void offerNewDataCheck(int deviceAddress, boolean forceAllDataAsNew) {
    // SimaticItemData data = SimaticProtocol.compileNewDataFrame(deviceAddress, forceAllDataAsNew);
    //
    // lock.lock();
    // // check if packet already exist
    // if (!dataInQueue(data)) {
    // commandQueue.offer(data);
    // }
    // lock.unlock();
    //
    // // processCommandQueue();
    // }
    //
    // /**
    // * Put "check new data" packet of specified device in front of command queue
    // *
    // * @param deviceAddress Device address
    // */
    // protected void offerNewDataCheckPriority(int deviceAddress) {
    // offerNewDataCheckPriority(deviceAddress, false);
    // }
    //
    // /**
    // * Put "check new data" packet of specified device in front of command queue
    // *
    // * @param deviceAddress Device address
    // * @param forceAllDataAsNew Flag to force send all data from slave
    // */
    // protected void offerNewDataCheckPriority(int deviceAddress, boolean forceAllDataAsNew) {
    // SimaticItemData data = SimaticProtocol.compileNewDataFrame(deviceAddress, forceAllDataAsNew);
    //
    // lock.lock();
    // commandQueue.addFirst(data);
    // lock.unlock();
    // }
    //
    // /**
    // * Check if data packet is not already in queue
    // *
    // * @param item
    // * @return
    // */
    // protected boolean dataInQueue(SimaticItemData item) {
    // if (commandQueue.isEmpty()) {
    // return false;
    // }
    //
    // for (SimaticItemData qitem : commandQueue) {
    // if (qitem.getData().length != item.getData().length) {
    // break;
    // }
    //
    // for (int i = 0; i < qitem.getData().length; i++) {
    // if (qitem.getData()[i] != item.getData()[i]) {
    // break;
    // }
    //
    // if (i == qitem.getData().length - 1) {
    // return true;
    // }
    // }
    // }
    //
    // return false;
    // }

    /**
     * Check if queue has data for specified device and send it
     *
     */
    protected void processCommandQueue(int thisDeviceOnly) {
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

        SimaticItemData dataToSend = null;
        //
        // try {
        // // queue is empty -> exit
        // if (commandQueue.isEmpty()) {
        // return;
        // }
        //
        // for (SimaticItemData i : commandQueue) {
        // if (i.deviceId == thisDeviceOnly) {
        // commandQueue.removeFirstOccurrence(i);
        // dataToSend = i;
        // break;
        // }
        // }
        // } finally {
        // lock.unlock();
        // }

        if (dataToSend != null) {
            sendDataOut(dataToSend);
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

        SimaticItemData dataToSend = null;

        // try {
        // // queue is empty -> exit
        // if (commandQueue.isEmpty()) {
        // return;
        // }
        //
        // // check first command in queue
        // SimaticItemData firstdata = commandQueue.peek();
        // // state of command device
        // DeviceStates state = this.devicesStates.getDeviceState(firstdata.deviceId);
        //
        // // check if device responds and there is lot of commands
        // if (state != DeviceStates.NOT_RESPONDING && (commandQueue.size() > 1)) {
        // dataToSend = commandQueue.poll();
        // } else {
        // // reorder queue - all commands from dead device put at the end of the queue
        // List<SimaticItemData> deadData = new ArrayList<SimaticItemData>();
        //
        // SimaticItemData data = firstdata;
        //
        // // over all items until item isn't same as first one
        // do {
        // if (data.deviceId == firstdata.deviceId) {
        // deadData.add(data);
        // } else {
        // commandQueue.offer(data);
        // }
        //
        // data = commandQueue.poll();
        //
        // if (firstdata == data) {
        // break;
        // }
        //
        // } while (true);
        //
        // // TODO: at begin put "check new data"??? - but only if ONCHANGE. What if ONSCAN???
        //
        // // add dead device data
        // commandQueue.addAll(deadData);
        //
        // // put first command (no matter if device not responding)
        // dataToSend = commandQueue.poll();
        // }
        // } catch (Exception e) {
        // } finally {
        // lock.unlock();
        // }

        if (dataToSend != null) {
            sendDataOut(dataToSend);
        }
    }

    protected boolean canSend() {
        return true;
    }

    /**
     * Write data into device stream
     *
     * @param data
     *            Item data with compiled packet
     * @return
     *         Return true when data were sent
     */
    protected boolean sendDataOut(SimaticItemData data) {

        logger.warn("{} - Generic device cant send data", this.toString());

        return false;
    }

    // /**
    // * Resend last sended data
    // */
    // protected void resendData(SimaticItemData lastData) {
    // if (lastData != null) {
    // if (lastData.getResendCounter() < MAX_RESEND_COUNT) {
    // lastData.incrementResendCounter();
    // sendDataPriority(lastData);
    // } else {
    // logger.warn("{} - Device {} - Max resend attempts reached.", this.toString(), lastData.getDeviceId());
    // // set state
    // devicesStates.setDeviceState(this.deviceName, lastData.getDeviceId(), DeviceStates.RESPONSE_ERROR);
    // }
    // }
    // }

    // /**
    // * Print communication information
    // *
    // */
    // protected void printCommunicationInfo(SimaticByteBuffer inBuffer, SimaticItemData lastSentData) {
    // try {
    // // content of input buffer
    // int position = inBuffer.position();
    // inBuffer.rewind();
    // byte[] data = new byte[inBuffer.limit()];
    // inBuffer.get(data);
    // inBuffer.position(position);
    // logger.info("{} - Data in input buffer: {}", toString(), SimaticProtocol.arrayToString(data, data.length));
    //
    // if (lastSentData != null) {
    // // last data out
    // logger.info("{} - Last sent data: {}", toString(),
    // SimaticProtocol.arrayToString(lastSentData.getData(), lastSentData.getData().length));
    // }
    // } catch (ModeChangeException e) {
    // logger.error(e.getMessage());
    // }
    // }

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

            for (SimaticReadWriteDataArea item : readAreasList.getData()) {
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

        SimaticReadWriteDataArea readDataArea = null;

        // prepare read queues
        for (Map.Entry<String, SimaticBindingConfig> item : list) {
            if (readDataArea == null || readDataArea.isItemOutOfRange(item.getValue())) {
                readDataArea = new SimaticReadWriteDataArea(item.getValue());
                readAreasList.put(readDataArea);
            } else {
                try {
                    readDataArea.addItem(item.getValue());
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
        }
    }

    public void postValue(SimaticBindingConfig item, byte[] buffer, int position) {
        ByteBuffer bb = ByteBuffer.wrap(buffer, position, item.getDataLenght());
        State state = null;
        Class<?> itemclass = item.getOpenHabItem().getClass();

        // no byte swap for array
        if (item.datatype == SimaticTypes.ARRAY) {
            bb.order(ByteOrder.BIG_ENDIAN);
        } else {
            bb.order(ByteOrder.LITTLE_ENDIAN);
        }

        if (item.datatype == SimaticTypes.ARRAY) {
            if (itemclass.isAssignableFrom(StringItem.class)) {
                String str = new String(buffer, position, item.getDataLenght());
                state = new StringType(str);
            } else {
                logger.warn("{} - Incoming data item {} - Array is only supported for string item.", toString(),
                        item.getName());
            }
        } else {

            if (itemclass.isAssignableFrom(ColorItem.class)) {
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
                int intValue = 0;

                if (item.address.dataType == SimaticPLCDataTypes.BIT) {
                    intValue = ((bb.get() & (2 ^ item.getAddress().getBitOffset())) != 0 ? 1 : 0);
                } else if (item.address.dataType == SimaticPLCDataTypes.BYTE) {
                    intValue = bb.get();
                } else if (item.address.dataType == SimaticPLCDataTypes.WORD) {
                    intValue = bb.getShort();
                } else if (item.address.dataType == SimaticPLCDataTypes.DWORD) {
                    intValue = bb.getInt();
                }

                if (itemclass.isAssignableFrom(NumberItem.class)) {
                    if (item.datatype == SimaticTypes.FLOAT) {
                        if (item.address.dataType == SimaticPLCDataTypes.DWORD) {
                            state = new DecimalType(bb.getFloat());
                        } else {
                            logger.warn("{} - Incoming data item {} - Float is only supported with DWORD address.",
                                    toString(), item.getName());
                        }
                    } else {
                        state = new DecimalType(intValue);
                    }
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
                    logger.warn("{} - Incoming data item {} - Class {} is not supported.", toString(), item.getName(),
                            itemclass.toString());
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
