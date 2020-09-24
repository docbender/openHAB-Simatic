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
import java.nio.charset.Charset;

import org.openhab.binding.simatic.internal.SimaticBindingConstants;
import org.openhab.binding.simatic.internal.libnodave.Nodave;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.types.Command;

/**
 *
 * Class holding single write data request
 *
 * @author Vita Tucek
 * @since 1.14.0
 */
public class SimaticWriteDataArea implements SimaticIReadWriteDataArea {
    static int INCREASE_STEP = 5;
    /** data limit PDU size depending **/
    int dataLimit = MAX_DATA_LENGTH;

    protected byte[] itemData;

    protected SimaticPLCAddress address;

    public static SimaticWriteDataArea create(Command command, SimaticChannel channel, int pduSize, Charset charset)
            throws Exception {

        var address = channel.getCommandAddress();

        if (address == null) {
            throw new Exception("Cannot create WriteDataArea. Command address not specified.");
        }

        byte[] data = null;

        if (channel.channelType.getId().equals(SimaticBindingConstants.CHANNEL_NUMBER)) {
            if (!(command instanceof DecimalType)) {
                throw new Exception(
                        String.format("Cannot create WriteDataArea. Command for ChannelType=%s must be DecimalType",
                                channel.channelType.getId()));
            }
            DecimalType cmd = (DecimalType) command;
            if (address.getSimaticDataType() == SimaticPLCDataTypes.BYTE) {
                data = new byte[] { cmd.byteValue() };
            } else if (address.getSimaticDataType() == SimaticPLCDataTypes.WORD) {
                data = new byte[] { (byte) ((cmd.intValue() >> 8) & 0xFF), (byte) (cmd.intValue() & 0xFF) };
            } else if (address.getSimaticDataType() == SimaticPLCDataTypes.DWORD) {
                if (address.isFloat()) {
                    float value = cmd.floatValue();
                    int bits = Float.floatToIntBits(value);
                    data = new byte[] { (byte) ((bits >> 24) & 0xFF), (byte) ((bits >> 16) & 0xFF),
                            (byte) ((bits >> 8) & 0xFF), (byte) (bits & 0xFF) };
                } else {
                    data = new byte[] { (byte) ((cmd.intValue() >> 24) & 0xFF), (byte) ((cmd.intValue() >> 16) & 0xFF),
                            (byte) ((cmd.intValue() >> 8) & 0xFF), (byte) (cmd.intValue() & 0xFF) };
                }
            } else {
                throw new Exception(String.format(
                        "Cannot create WriteDataArea. Command for ChannelType=%s has unsupported datatype=%s",
                        channel.channelType.getId(), address.getSimaticDataType()));
            }
        } else if (channel.channelType.getId().equals(SimaticBindingConstants.CHANNEL_STRING)) {

            if (!(command instanceof StringType)) {
                throw new Exception(
                        String.format("Cannot create WriteDataArea. Command for ChannelType=%s must be StringType",
                                channel.channelType.getId()));
            }
            StringType cmd = (StringType) command;
            String str = cmd.toString();

            data = new byte[address.getDataLength()];

            var bytes = str.getBytes(charset);

            for (int i = 0; i < address.getDataLength(); i++) {
                if (str.length() <= address.getDataLength() && bytes.length <= address.getDataLength()) {
                    data[i] = bytes[i];
                } else {
                    data[i] = 0x0;
                }
            }
        } else if (channel.channelType.getId().equals(SimaticBindingConstants.CHANNEL_SWITCH)) {
            if (!(command instanceof OnOffType)) {
                throw new Exception(
                        String.format("Cannot create WriteDataArea. Command for ChannelType=%s must be OnOffType",
                                channel.channelType.getId()));
            }

            OnOffType cmd = (OnOffType) command;

            if (cmd == OnOffType.ON) {
                data = new byte[] { 1 };
            } else {
                data = new byte[] { 0 };
            }
        } else if (channel.channelType.getId().equals(SimaticBindingConstants.CHANNEL_CONTACT)) {
            if (!(command instanceof OpenClosedType)) {
                throw new Exception(
                        String.format("Cannot create WriteDataArea. Command for ChannelType=%s must be OpenClosedType",
                                channel.channelType.getId()));
            }

            OpenClosedType cmd = (OpenClosedType) command;

            if (cmd == OpenClosedType.OPEN) {
                data = new byte[] { 1 };
            } else {
                data = new byte[] { 0 };
            }
        } else if (channel.channelType.getId().equals(SimaticBindingConstants.CHANNEL_COLOR)) {
            if (command instanceof HSBType) {
                long red = Math.round((((HSBType) command).getRed().doubleValue() * 2.55));
                long green = Math.round((((HSBType) command).getGreen().doubleValue() * 2.55));
                long blue = Math.round((((HSBType) command).getBlue().doubleValue() * 2.55));

                if (red > 255) {
                    red = 255;
                }
                if (green > 255) {
                    green = 255;
                }
                if (blue > 255) {
                    blue = 255;
                }

                data = new byte[] { (byte) (red & 0xFF), (byte) (green & 0xFF), (byte) (blue & 0xFF), 0x0 };
            } else {
                throw new Exception(
                        String.format("Cannot create WriteDataArea. Command %s for ChannelType=%s not implemented",
                                command.getClass(), channel.channelType.getId()));
            }
        } else if (channel.channelType.getId().equals(SimaticBindingConstants.CHANNEL_DIMMER)) {
            if (command instanceof PercentType) {
                data = new byte[] { ((PercentType) command).byteValue() };
            } else if (command instanceof OnOffType) {
                if (((OnOffType) command) == OnOffType.ON) {
                    data = new byte[] { 100 };
                } else {
                    data = new byte[] { 0 };
                }
            } else {
                throw new Exception(
                        String.format("Cannot create WriteDataArea. Command %s for ChannelType=%s not implemented",
                                command.getClass(), channel.channelType.getId()));
            }
        } else if (channel.channelType.getId().equals(SimaticBindingConstants.CHANNEL_ROLLERSHUTTER)) {
            if (command instanceof StopMoveType) {
                data = new byte[] { (byte) (((StopMoveType) command).equals(StopMoveType.MOVE) ? 0x1 : 0x2) };
            } else if (command instanceof UpDownType) {
                data = new byte[] { (byte) (((UpDownType) command).equals(UpDownType.UP) ? 0x4 : 0x8) };
            } else {
                throw new Exception(
                        String.format("Cannot create WriteDataArea. Command %s for ChannelType=%s not implemented",
                                command.getClass(), channel.channelType.getId()));
            }
        } else {
            throw new Exception(
                    String.format("Cannot create WriteDataArea. Command for ChannelType=%s not implemented.",
                            channel.channelType.getId()));
        }

        return new SimaticWriteDataArea(address, data, pduSize);
    }

    /**
     * Construct item data instance for unspecified item
     *
     * @param itemData
     *            Raw data
     */
    public SimaticWriteDataArea(SimaticPLCAddress address, byte[] itemData, int pduSize) {
        this.address = address;
        this.itemData = itemData;
        if (pduSize > WRITE_OVERHEAD) {
            dataLimit = pduSize - WRITE_OVERHEAD;
        }
    }

    /**
     * Return item raw data
     *
     * @return
     */
    public byte[] getData() {
        return itemData;
    }

    /**
     * Return item address
     *
     * @return
     */
    public SimaticPLCAddress getAddress() {
        return address;
    }

    /**
     * Calculate white part for RGB
     *
     * @param red
     * @param green
     * @param blue
     * @return
     */
    private static byte[] calcWhite(long red, long green, long blue) {

        byte[] result = new byte[4];
        float M = Math.max(Math.max(red, green), blue);
        float m = Math.min(Math.min(red, green), blue);

        float Wo = 0;

        double Ro = 0;
        double Go = 0;
        double Bo = 0;

        if (m > 0) {
            if (m / M < 0.5) {
                Wo = (m * M) / (M - m);
            } else {
                Wo = M;
            }

            // int Q = 100;

            float K = m / (Wo + M);

            Ro = Math.floor(red - K * Wo);
            Go = Math.floor(green - K * Wo);
            Bo = Math.floor(blue - K * Wo);
        } else {
            Ro = red;
            Go = green;
            Bo = blue;
            Wo = 0;
        }

        result[0] = (byte) Math.round(Ro);
        result[1] = (byte) Math.round(Go);
        result[2] = (byte) Math.round(Bo);
        result[3] = (byte) Math.round(Math.floor(Wo));

        return result;
    }

    @Override
    public SimaticPLCAreaTypes getArea() {
        return address.getArea();
    }

    @Override
    public int getAreaIntFormat() {
        switch (address.getArea()) {
            case I:
                return Nodave.INPUTS;
            case Q:
                return Nodave.OUTPUTS;
            case DB:
                return Nodave.DB;
            case M:
                return Nodave.FLAGS;
            default:
                break;
        }

        return -1;
    }

    @Override
    public int getDBNumber() {
        return address.getDBNumber();
    }

    @Override
    public int getStartAddress() {
        return address.getByteOffset();
    }

    @Override
    public int getAddressSpaceLength() {
        return address.getDataLength();
    }

    @Override
    public boolean isItemOutOfRange(SimaticPLCAddress itemAddress) {
        // must be in area, eventually same DB, not bit, without any space between data, in max frame size
        return itemAddress.getArea() != this.getArea()
                || (this.getArea() == SimaticPLCAreaTypes.DB && address.getDBNumber() != itemAddress.getDBNumber())
                || itemAddress.getSimaticDataType() == SimaticPLCDataTypes.BIT
                || this.address.getSimaticDataType() == SimaticPLCDataTypes.BIT
                || (itemAddress.getByteOffset() > (this.address.getByteOffset() + this.address.getDataLength()))
                || ((itemAddress.getByteOffset() + itemAddress.getDataLength()) < this.address.getByteOffset())
                || (itemAddress.getByteOffset() + itemAddress.getDataLength()
                        - this.address.getByteOffset() > dataLimit)
                || (this.address.getByteOffset() + this.getAddressSpaceLength()
                        - itemAddress.getByteOffset() > dataLimit);
    }

    public void insert(SimaticWriteDataArea data) {
        if (this.getStartAddress() > data.getStartAddress()) {
            // new length
            this.address
                    .setDataLength((((this.getStartAddress() + this.getAddressSpaceLength()) > (data.getStartAddress()
                            + data.getAddressSpaceLength())) ? (this.getStartAddress() + this.getAddressSpaceLength())
                                    : (data.getStartAddress() + data.getAddressSpaceLength()))
                            - this.address.getByteOffset());
            // data array
            ByteBuffer newDataBuffer = ByteBuffer.wrap(new byte[this.address.getDataLength()]);
            // put new data
            newDataBuffer.put(data.getData());
            // and behind rest of old
            newDataBuffer.put(this.getData(),
                    data.getAddressSpaceLength() - (this.getStartAddress() - data.getStartAddress()),
                    this.getAddressSpaceLength()
                            - (data.getAddressSpaceLength() - (this.getStartAddress() - data.getStartAddress())));
            // new start address
            this.address.setByteOffset(data.getStartAddress());
            this.itemData = newDataBuffer.array();

        } else if (this.getStartAddress() < data.getStartAddress()) {
            if ((this.getStartAddress() + this.getAddressSpaceLength()) < (data.getStartAddress()
                    + data.getAddressSpaceLength())) {
                int oldDataLength = this.address.getDataLength();
                this.address.setDataLength(oldDataLength + (data.getStartAddress() + data.getAddressSpaceLength())
                        - (this.getStartAddress() + this.getAddressSpaceLength()));
                // data array
                byte[] newData = new byte[this.address.getDataLength()];
                // old data
                for (int i = 0; i < oldDataLength; i++) {
                    newData[i] = this.itemData[i];
                }
                int offset = data.getStartAddress() - this.getStartAddress();
                // then new data
                for (int i = 0; i < data.getAddressSpaceLength(); i++) {
                    newData[offset + i] = data.itemData[i];
                }
            }
        } else if (this.getAddressSpaceLength() < data.getAddressSpaceLength()) {
            int oldDataLength = this.address.getDataLength();
            this.address.setDataLength(oldDataLength + data.getAddressSpaceLength() - this.getAddressSpaceLength());
            // data array
            byte[] newData = new byte[this.address.getDataLength()];
            // old data
            for (int i = 0; i < oldDataLength; i++) {
                newData[i] = this.itemData[i];
            }
            // then new data
            for (int i = 0; i < data.getAddressSpaceLength(); i++) {
                newData[i] = data.itemData[i];
            }
        } else {
            // write new data over old
            for (int i = 0; i < data.getAddressSpaceLength(); i++) {
                itemData[i] = data.itemData[i];
            }
        }
    }
}
