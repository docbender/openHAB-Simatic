/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal.simatic;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simatic.internal.SimaticBindingConstants;
import org.openhab.binding.simatic.internal.simatic.SimaticPortState.PortStates;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic device class
 *
 * @author Vita Tucek
 * @since 1.14.0
 */
public class SimaticGenericDevice implements SimaticIDevice {
    private static final Logger logger = LoggerFactory.getLogger(SimaticGenericDevice.class);
    private static final String THING_HANDLER_THREADPOOL_NAME = "thingHandler";
    protected final ScheduledExecutorService scheduler = ThreadPoolManager
            .getScheduledPool(THING_HANDLER_THREADPOOL_NAME);

    private static final int RECONNECT_DELAY_MAX = 15;
    private int rcTest = 0;
    private int rcTestMax = 0;

    /** defines maximum resend count */
    public final int MAX_RESEND_COUNT = 2;

    /** item config */
    protected List<@NonNull SimaticChannel> stateItems;

    /** flag that device is connected */
    private boolean connected = false;
    /** queue for commands */
    protected final Deque<SimaticWriteDataArea> commandQueue = new LinkedList<SimaticWriteDataArea>();
    /** State of socket */
    public SimaticPortState portState = new SimaticPortState();
    /** Lock for process commands to prevent run it twice **/
    protected final Lock lock = new ReentrantLock();
    protected final Lock readLock = new ReentrantLock();
    /** Read PLC areas **/
    protected final SimaticReadQueue readAreasList = new SimaticReadQueue();
    /** try reconnect flag when read/write function failure **/
    protected final AtomicBoolean tryReconnect = new AtomicBoolean(false);
    /** PDU size **/
    protected int pduSize = 0;
    protected final Charset charset;

    protected boolean disposed = false;

    long readed = 0;
    long readedBytes = 0;
    long metricsStart = 0;

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

    private @Nullable ScheduledFuture<?> periodicJob = null;
    private @Nullable ScheduledFuture<?> reconnectJob = null;

    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    /**
     * Constructor
     *
     */
    public SimaticGenericDevice(int pollRate, Charset charset) {
        this.charset = charset;
        if (pollRate > 0) {
            periodicJob = scheduler.scheduleAtFixedRate(() -> {
                if (!reconnecting.get()) {
                    execute();
                }
            }, 500, pollRate, TimeUnit.MILLISECONDS);
        } else {
            scheduler.execute(() -> {
                while (!disposed) {
                    if (!reconnecting.get()) {
                        execute();
                    } else {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {

                        }
                    }
                }
            });
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        close();
        if (periodicJob != null) {
            periodicJob.cancel(true);
            periodicJob = null;
        }
        if (reconnectJob != null) {
            reconnectJob.cancel(true);
            reconnectJob = null;
        }
    }

    /**
     * Called at specified period
     */
    protected void execute() {
        if (shouldReconnect()) {
            reconnectWithDelaying();
        }
        if (isConnected()) {
            // check device for new data
            checkNewData();
        }
    }

    @Override
    public void setDataAreas(@NonNull ArrayList<@NonNull SimaticChannel> stateItems) {
        this.stateItems = stateItems;
        // prepare data if device is connected (depends on PDU size)
        if (isConnected()) {
            prepareData();
        }
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
        if (logger.isDebugEnabled()) {
            logger.debug("{} - close()", this.toString());
        }
        portState.setState(PortStates.CLOSED);
        setConnected(false);
    }

    /**
     * Set connection state
     */
    protected void setConnected(boolean state) {
        if (connected == state) {
            return;
        }
        connected = state;
        if (onChange != null) {
            try {
                onChange.onConnectionChanged(state);
            } catch (Exception ex) {
                logger.error("{} - ", this.toString(), ex);
            }
        }
    }

    /**
     * Reconnect device
     */
    public boolean reconnect() {
        logger.info("{} - Trying to reconnect", toString());

        close();
        return open();
    }

    // TODO: speed test - measure read transaction and find bottleneck

    /**
     * Reconnect device
     */
    protected void reconnectWithDelaying() {
        if (!reconnecting.compareAndSet(false, true)) {
            logger.debug("{} - reconnectJob(): already running", toString());
            return;
        }

        if (reconnectJob != null) {
            logger.debug("{} - reconnectJob(): canceling previous instance", toString());
            reconnectJob.cancel(true);
            reconnectJob = null;
        }

        logger.debug("{} - reconnectJob(): create", toString());

        reconnectJob = scheduler.scheduleAtFixedRate(() -> {
            logger.debug("{} - reconnectJob(): {}/{}/{}", toString(), rcTest, rcTestMax, RECONNECT_DELAY_MAX);

            if (rcTest < rcTestMax) {
                rcTest++;
                return;
            }

            if (reconnect()) {
                rcTest = 0;
                rcTestMax = 0;

                logger.debug("{} - reconnectJob(): reconnected", toString());
                if (reconnectJob.cancel(false)) {
                    reconnectJob = null;
                    logger.debug("{} - reconnectJob(): canceled", toString());
                    reconnecting.set(false);
                }
            } else {
                if (rcTestMax <= RECONNECT_DELAY_MAX) {
                    rcTestMax++;
                }
                rcTest = 0;
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * Send command into device channel
     *
     * @see org.openhab.binding.SimaticIDevice.internal.SimaticIDevice#sendData(java.lang.String,
     *      org.openhab.core.types.Command)
     */
    @Override
    public void sendData(SimaticChannel item, Command command) {
        try {
            var area = SimaticWriteDataArea.create(command, item, pduSize, charset);
            sendData(area);
        } catch (Exception ex) {
            logger.error("{} - ", toString(), ex);
        }
    }

    /**
     * Add compiled data item to sending queue
     *
     * @param data
     */
    private void sendData(SimaticWriteDataArea data) {
        if (data == null) {
            logger.warn("{} - Nothing to send. Empty data area", toString());
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("{} - Adding command into queue", toString());
        }

        // lock queue
        lock.lock();

        try {
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
        } catch (Exception ex) {
            logger.error("{} - Cannot insert data into command queue.", toString(), ex);
        } finally {
            // unlock queue
            lock.unlock();
        }

        processCommandQueue();
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
            logger.error("{} - Cannost retrieve data from command queue.", toString(), e);
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
     * Check new data for all connected devices
     */
    protected void checkNewData() {
        if (!isConnected()) {
            return;
        }

        if (logger.isTraceEnabled()) {
            logger.trace("{} - checkNewData() is called", toString());
        }

        if (!readLock.tryLock()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - Reading already in progress", toString());
            }
            return;
        }

        logger.trace("{} - Locking", toString());

        try {
            for (SimaticReadDataArea area : readAreasList.getData()) {
                try {
                    // read data
                    readDataArea(area);
                } catch (SimaticReadException e) {
                    logger.error("{} - ", toString(), e);
                    if (e.fatal) {
                        return;
                    } else {
                        continue;
                    }
                }

                readed++;
                readedBytes += area.getAddressSpaceLength();

                if (logger.isDebugEnabled()) {
                    logger.debug("{} - Reading finished. Area={}", toString(), area.toString());
                }
            }
        } catch (Exception ex) {
            logger.error("{} - Read data error", toString(), ex);
        } finally {
            logger.trace("{} - Unlocking", toString());
            readLock.unlock();

            long diff;
            if ((diff = (System.currentTimeMillis() - metricsStart)) >= 5000 || metricsStart == 0) {
                long requests = (long) Math.ceil(readed * 1000.0 / diff);
                long bytes = (long) Math.ceil(readedBytes * 1000.0 / diff);

                metricsStart = System.currentTimeMillis();
                readed = readedBytes = 0;

                if (onUpdate != null) {
                    onUpdate.onMetricsUpdated(requests, bytes);
                }
            }
        }
    }

    @Override
    public void readDataArea(SimaticReadDataArea area) throws SimaticReadException {
    }

    /**
     * After item configuration is loaded this method prepare reading areas for this device
     */
    public void prepareData() {
        if (stateItems == null) {
            return;
        }

        // sort items by address
        Collections.sort(stateItems, new Comparator<SimaticChannel>() {
            @Override
            public int compare(SimaticChannel o1, SimaticChannel o2) {
                var o1A = o1.getStateAddress();
                if (o1A == null) {
                    if (o2.getStateAddress() == null) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
                return (o1A).compareTo(o2.getStateAddress());
            }
        });

        // This lock achieves two things:
        // 1) It blocks until checkNewData finishes reading, so that readAreasList isn't null white checkNewData access
        // it
        // 2) It doesn't allow checkNewData to run until readAreasList is propagated
        // -- AchilleGR

        readLock.lock();
        if (logger.isDebugEnabled()) {
            logger.debug("{} - prepareData Locked", this.toString());
            int readLimit = pduSize > 0 ? pduSize - SimaticIReadWriteDataArea.READ_OVERHEAD
                    : SimaticIReadWriteDataArea.MAX_DATA_LENGTH;
            int writeLimit = pduSize > 0 ? pduSize - SimaticIReadWriteDataArea.WRITE_OVERHEAD
                    : SimaticIReadWriteDataArea.MAX_DATA_LENGTH;
            logger.debug("{} - read area data limit = {}B, write area data limit = {}B", this.toString(), readLimit,
                    writeLimit);
        }
        SimaticReadDataArea readDataArea = null;
        readAreasList.clear();

        // prepare read queues
        for (SimaticChannel item : stateItems) {
            // no data for read
            if (item.getStateAddress() == null) {
                continue;
            }

            //
            if (readDataArea == null || readDataArea.isItemOutOfRange(item.getStateAddress())) {
                readDataArea = new SimaticReadDataArea(item, pduSize);
                readAreasList.put(readDataArea);
            } else {
                try {
                    readDataArea.addItem(item);
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
        }

        if (logger.isDebugEnabled()) {
            StringBuilder message = new StringBuilder();
            message.append(String.format("%s - readAreas(Size=%d):", this.toString(), readAreasList.data.size()));

            for (SimaticReadDataArea i : readAreasList.getData()) {
                message.append(i.toString());
                message.append(";");
            }

            logger.debug(message.toString());
        }
        logger.debug("{} - prepareData Unlocking", this.toString());
        readLock.unlock();
    }

    /**
     * Method decode incoming data and on success post this into openHAB
     *
     * @param item
     * @param buffer
     * @param position
     */
    @SuppressWarnings("null")
    public void postValue(SimaticChannel item, byte[] buffer, int position) {
        // logger.debug("item={}", item.toString());
        // logger.debug("buffer={}", buffer.length);
        // logger.debug("position={}", position);
        // logger.debug("item len={}", item.getStateAddress().getDataLength());
        try {
            ByteBuffer bb = ByteBuffer.wrap(buffer, position, item.getStateAddress().getDataLength());

            // no byte swap for array
            // if (item.datatype == SimaticTypes.ARRAY) {
            bb.order(ByteOrder.BIG_ENDIAN);
            // } else {
            // bb.order(ByteOrder.LITTLE_ENDIAN);
            // }

            if (item.channelType.getId().equals(SimaticBindingConstants.CHANNEL_STRING)) {
                // check for '\0' char and resolve string length
                int i;
                for (i = position; i < item.getStateAddress().getDataLength(); i++) {
                    if (buffer[i] == 0) {
                        break;
                    }
                }
                String str = new String(buffer, position, i, charset);
                item.setState(new StringType(str));

            } else if (item.channelType.getId().equals(SimaticBindingConstants.CHANNEL_NUMBER)) {
                if (item.getStateAddress().isFloat()) {
                    item.setState(new DecimalType(bb.getFloat()));
                } else {
                    final int intValue;
                    if (item.getStateAddress().getSimaticDataType() == SimaticPLCDataTypes.BIT) {
                        intValue = (bb.get() & (int) Math.pow(2, item.getStateAddress().getBitOffset())) != 0 ? 1 : 0;
                    } else if (item.getStateAddress().getSimaticDataType() == SimaticPLCDataTypes.BYTE) {
                        intValue = bb.get();
                    } else if (item.getStateAddress().getSimaticDataType() == SimaticPLCDataTypes.WORD) {
                        intValue = bb.getShort();
                    } else if (item.getStateAddress().getSimaticDataType() == SimaticPLCDataTypes.DWORD) {
                        intValue = bb.getInt();
                    } else {
                        intValue = 0;
                    }

                    item.setState(new DecimalType(intValue));
                }
            } else if (item.channelType.getId().equals(SimaticBindingConstants.CHANNEL_DIMMER)) {
                item.setState(new PercentType(bb.get()));
            } else if (item.channelType.getId().equals(SimaticBindingConstants.CHANNEL_CONTACT)) {
                if (item.getStateAddress().getSimaticDataType() == SimaticPLCDataTypes.BIT) {
                    item.setState((bb.get() & (int) Math.pow(2, item.getStateAddress().getBitOffset())) != 0
                            ? OpenClosedType.OPEN
                            : OpenClosedType.CLOSED);
                } else {
                    item.setState((bb.get() != 0) ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                }
            } else if (item.channelType.getId().equals(SimaticBindingConstants.CHANNEL_SWITCH)) {
                if (item.getStateAddress().getSimaticDataType() == SimaticPLCDataTypes.BIT) {
                    item.setState(
                            (bb.get() & (int) Math.pow(2, item.getStateAddress().getBitOffset())) != 0 ? OnOffType.ON
                                    : OnOffType.OFF);
                } else {
                    item.setState(bb.get() != 0 ? OnOffType.ON : OnOffType.OFF);
                }
            } else if (item.channelType.getId().equals(SimaticBindingConstants.CHANNEL_ROLLERSHUTTER)) {
                item.setState(new PercentType(bb.get()));
            } else if (item.channelType.getId().equals(SimaticBindingConstants.CHANNEL_COLOR)) {
                byte b0 = bb.get();
                byte b1 = bb.get();
                byte b2 = bb.get();
                // byte b3 = bb.get();

                item.setState(HSBType.fromRGB(b0 & 0xFF, b1 & 0xFF, b2 & 0xFF));
            } else {
                item.setState(null);
                logger.warn("{} - Incoming data channel {} - Unsupported channel type {}.", toString(), item.channelId,
                        item.channelType.getId());
                return;
            }
        } catch (Exception ex) {
            logger.error("{} - Incoming data post error. Item:{}/state:{}.", toString(), item.channelId,
                    item.getState(), ex);
        }

        if (logger.isTraceEnabled()) {
            logger.trace("{} - Incoming data - item:{}/state:{}", toString(), item.channelId, item.getState());
        }
    }

    public boolean shouldReconnect() {
        return tryReconnect.get();
    }

    public int getPduSize() {
        return pduSize;
    }

    public SimaticReadQueue getReadAreas() {
        return readAreasList;
    }

    private ConnectionChanged onChange = null;

    @Override
    public void onConnectionChanged(ConnectionChanged onChangeMethod) {
        onChange = onChangeMethod;
    }

    private MetricsUpdated onUpdate = null;

    @Override
    public void onMetricsUpdated(MetricsUpdated onUpdateMethod) {
        onUpdate = onUpdateMethod;
    }

    public static String arrayToString(byte[] data, int length) {
        StringBuilder s = new StringBuilder();

        for (int i = 0; i < length; i++) {
            byte b = data[i];
            if (s.length() == 0) {
                s.append("[");
            } else {
                s.append(" ");
            }

            // if(SimpleBinaryBinding.JavaVersion >= 1.8)
            // s.append("0x" + Integer.toHexString(Byte.toUnsignedInt(b)).toUpperCase());
            // else
            s.append("0x" + Integer.toHexString(b & 0xFF).toUpperCase());
        }

        s.append("]");

        return s.toString();
    }
}
