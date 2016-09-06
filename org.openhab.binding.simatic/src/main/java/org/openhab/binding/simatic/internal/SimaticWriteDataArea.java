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

import org.openhab.binding.simatic.internal.SimaticGenericBindingProvider.SimaticBindingConfig;
import org.openhab.binding.simatic.libnodave.Nodave;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.ColorItem;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.items.RollershutterItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class holding single write data request
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimaticWriteDataArea implements SimaticIReadWriteDataArea {

    private static final Logger logger = LoggerFactory.getLogger(SimaticWriteDataArea.class);

    static int INCREASE_STEP = 5;

    public static SimaticWriteDataArea create(Command command, SimaticBindingConfig config) {
        if (logger.isDebugEnabled()) {
            logger.debug("create(): item:" + config.getName() + "|datatype:" + config.getDataType());
        }

        byte[] data = null;

        switch (config.getDataType()) {
            case BYTE:
                if (command instanceof PercentType) {
                    PercentType cmd = (PercentType) command;
                    data = new byte[] { cmd.byteValue() };

                    if (config.getOpenHabItem().getClass().isAssignableFrom(DimmerItem.class)) {
                        ((DimmerItem) config.item).setState(new PercentType(cmd.byteValue()));
                    } else if (config.getOpenHabItem().getClass().isAssignableFrom(RollershutterItem.class)) {
                        ((RollershutterItem) config.item).setState(new PercentType(cmd.byteValue()));
                    }
                } else if (command instanceof DecimalType) {
                    DecimalType cmd = (DecimalType) command;
                    data = new byte[] { cmd.byteValue() };
                } else if (command instanceof OpenClosedType) {
                    OpenClosedType cmd = (OpenClosedType) command;
                    if (cmd == OpenClosedType.OPEN) {
                        data = new byte[] { 1 };
                    } else {
                        data = new byte[] { 0 };
                    }
                } else if (command instanceof OnOffType) {
                    OnOffType cmd = (OnOffType) command;

                    if (config.getOpenHabItem().getClass().isAssignableFrom(SwitchItem.class)) {
                        if (cmd == OnOffType.ON) {
                            data = new byte[] { 1 };
                        } else {
                            data = new byte[] { 0 };
                        }
                    } else if (config.getOpenHabItem().getClass().isAssignableFrom(DimmerItem.class)) {
                        if (cmd == OnOffType.ON) {
                            PercentType val = ((PercentType) (config.item).getStateAs(PercentType.class));
                            if (val == null) {
                                data = new byte[] { 100 };
                                ((DimmerItem) config.item).setState(PercentType.HUNDRED);
                            } else {
                                data = new byte[] { val.byteValue() };
                                ((DimmerItem) config.item).setState(PercentType.ZERO);
                            }
                        } else {
                            data = new byte[] { 0 };
                        }
                    } else {
                        logger.error("Unsupported command type {} for datatype {}", command.getClass().toString(),
                                config.getDataType());
                    }

                } else if (command instanceof IncreaseDecreaseType
                        && config.getOpenHabItem().getClass().isAssignableFrom(DimmerItem.class)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("IncreaseDecreaseType - DimmerItem");
                    }

                    DecimalType val = ((DecimalType) ((DimmerItem) (config.item)).getStateAs(DecimalType.class));

                    if (val == null) {
                        return null;
                    }

                    int brightness = Math.round((val.floatValue() * 100));

                    IncreaseDecreaseType upDownCmd = (IncreaseDecreaseType) command;

                    if (upDownCmd == IncreaseDecreaseType.INCREASE) {
                        brightness += INCREASE_STEP;

                        if (brightness > 100) {
                            brightness = 100;
                        }
                    } else {
                        brightness -= INCREASE_STEP;

                        if (brightness < 0) {
                            brightness = 0;
                        }
                    }

                    data = new byte[] { (byte) brightness };

                    ((DimmerItem) config.item).setState(new PercentType(brightness));
                } else {
                    logger.error("Unsupported command type {} for datatype {}", command.getClass().toString(),
                            config.getDataType());
                    return null;
                }
                break;
            case WORD:
                if (command instanceof PercentType) {
                    PercentType cmd = (PercentType) command;
                    data = new byte[] { cmd.byteValue(), 0x0 };
                } else if (command instanceof DecimalType) {
                    DecimalType cmd = (DecimalType) command;
                    data = new byte[] { (byte) ((cmd.intValue() >> 8) & 0xFF), (byte) (cmd.intValue() & 0xFF) };
                } else if (command instanceof StopMoveType) {
                    StopMoveType cmd = (StopMoveType) command;
                    data = new byte[] { 0x0, (byte) (cmd.equals(StopMoveType.MOVE) ? 0x1 : 0x2) };
                } else if (command instanceof UpDownType) {
                    UpDownType cmd = (UpDownType) command;
                    data = new byte[] { 0x0, (byte) (cmd.equals(UpDownType.UP) ? 0x4 : 0x8) };
                } else {
                    logger.error("Unsupported command type {} for datatype {}", command.getClass().toString(),
                            config.getDataType());
                    return null;
                }
                break;
            case DWORD:
                if (command instanceof DecimalType) {
                    DecimalType cmd = (DecimalType) command;
                    data = new byte[] { (byte) ((cmd.intValue() >> 24) & 0xFF), (byte) ((cmd.intValue() >> 16) & 0xFF),
                            (byte) ((cmd.intValue() >> 8) & 0xFF), (byte) (cmd.intValue() & 0xFF) };
                } else {
                    logger.error("Unsupported command type {} for datatype {}", command.getClass().toString(),
                            config.getDataType());
                    return null;
                }
                break;
            case FLOAT:
                if (command instanceof DecimalType) {
                    DecimalType cmd = (DecimalType) command;
                    float value = cmd.floatValue();
                    int bits = Float.floatToIntBits(value);

                    data = new byte[] { (byte) ((bits >> 24) & 0xFF), (byte) ((bits >> 16) & 0xFF),
                            (byte) ((bits >> 8) & 0xFF), (byte) (bits & 0xFF) };
                } else {
                    logger.error("Unsupported command type {} for datatype {}", command.getClass().toString(),
                            config.getDataType());
                    return null;
                }
                break;
            case HSB:
            case RGB:
            case RGBW:

                HSBType hsbVal;
                Item item = config.item;

                if (logger.isDebugEnabled()) {
                    logger.debug(item.toString());
                }

                if (command instanceof OnOffType) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("OnOffType");
                    }
                    OnOffType onOffCmd = (OnOffType) command;

                    if (onOffCmd == OnOffType.OFF) {
                        hsbVal = new HSBType(new Color(0, 0, 0));
                    } else {
                        hsbVal = ((HSBType) item.getStateAs(HSBType.class));
                    }
                } else if (command instanceof IncreaseDecreaseType) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("IncreaseDecreaseType");
                    }

                    hsbVal = ((HSBType) item.getStateAs(HSBType.class));
                    int brightness = hsbVal.getBrightness().intValue();

                    IncreaseDecreaseType upDownCmd = (IncreaseDecreaseType) command;

                    if (upDownCmd == IncreaseDecreaseType.INCREASE) {
                        brightness += INCREASE_STEP;

                        if (brightness > 100) {
                            brightness = 100;
                        }
                    } else {
                        brightness -= INCREASE_STEP;

                        if (brightness < 0) {
                            brightness = 0;
                        }
                    }

                    hsbVal = new HSBType(hsbVal.getHue(), hsbVal.getSaturation(), new PercentType(brightness));

                    ((ColorItem) item).setState(hsbVal);
                } else if (command instanceof HSBType) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("HSBType");
                    }
                    hsbVal = (HSBType) command;

                    ((ColorItem) item).setState(hsbVal);
                } else if (command instanceof PercentType) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("PercentType");
                    }
                    hsbVal = ((HSBType) item.getStateAs(HSBType.class));
                } else {
                    logger.error("Unsupported command type {} for datatype {}", command.getClass().toString(),
                            config.getDataType());
                    return null;
                }

                if (hsbVal != null) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Item {}: Red={} Green={} Blue={}", item.getName(), hsbVal.getRed(),
                                hsbVal.getGreen(), hsbVal.getBlue());
                        logger.trace("         Hue={} Sat={} Bri={}", hsbVal.getHue(), hsbVal.getSaturation(),
                                hsbVal.getBrightness());
                    }

                    HSBType cmd = hsbVal;

                    if (config.getDataType() == SimaticTypes.HSB) {
                        data = new byte[] { cmd.getHue().byteValue(), cmd.getSaturation().byteValue(),
                                cmd.getBrightness().byteValue(), 0x0 };
                    } else if (config.getDataType() == SimaticTypes.RGB) {
                        long red = Math.round((cmd.getRed().doubleValue() * 2.55));
                        long green = Math.round((cmd.getGreen().doubleValue() * 2.55));
                        long blue = Math.round((cmd.getBlue().doubleValue() * 2.55));

                        if (red > 255) {
                            red = 255;
                        }
                        if (green > 255) {
                            green = 255;
                        }
                        if (blue > 255) {
                            blue = 255;
                        }

                        if (logger.isDebugEnabled()) {
                            logger.debug("         Converted to 0-255: Red={} Green={} Blue={}", red, green, blue);
                        }

                        data = new byte[] { (byte) (red & 0xFF), (byte) (green & 0xFF), (byte) (blue & 0xFF), 0x0 };
                    } else if (config.getDataType() == SimaticTypes.RGBW) {
                        long red = Math.round((cmd.getRed().doubleValue() * 2.55));
                        long green = Math.round((cmd.getGreen().doubleValue() * 2.55));
                        long blue = Math.round((cmd.getBlue().doubleValue() * 2.55));
                        byte white;

                        if (red > 255) {
                            red = 255;
                        }
                        if (green > 255) {
                            green = 255;
                        }
                        if (blue > 255) {
                            blue = 255;
                        }

                        if (logger.isDebugEnabled()) {
                            logger.debug("         Converted to 0-255: Red={} Green={} Blue={}", red, green, blue);
                        }

                        byte[] rgbw = calcWhite(red, green, blue);
                        red = rgbw[0];
                        green = rgbw[1];
                        blue = rgbw[2];
                        white = rgbw[3];

                        if (logger.isDebugEnabled()) {
                            logger.debug("         Converted to RGBW: Red={} Green={} Blue={} White={}", red & 0xFF,
                                    green & 0xFF, blue & 0xFF, white & 0xFF);
                        }

                        data = new byte[] { (byte) (red & 0xFF), (byte) (green & 0xFF), (byte) (blue & 0xFF), white };
                    }
                }

                break;
            case ARRAY:
                if (command instanceof StringType) {
                    StringType cmd = (StringType) command;
                    String str = cmd.toString();

                    data = new byte[config.getAddress().getDataLength()];

                    for (int i = 0; i < config.getDataLength(); i++) {
                        if (str.length() <= config.getDataLength()) {
                            data[i] = (byte) str.charAt(i);
                        } else {
                            data[i] = 0x0;
                        }
                    }
                } else {
                    logger.error("Unsupported command type {} for datatype {}", command.getClass().toString(),
                            config.getDataType());
                    return null;
                }
                break;
            default:
                return null;
        }

        return new SimaticWriteDataArea(config.getAddress(), data);

    }

    protected byte[] itemData;
    protected SimaticPLCAddress address;

    /**
     * Construct item data instance for unspecified item
     *
     * @param itemData
     *            Raw data
     */
    public SimaticWriteDataArea(SimaticPLCAddress address, byte[] itemData) {
        this.address = address;
        this.itemData = itemData;
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
                || (this.getArea() == SimaticPLCAreaTypes.DB && !address.DBNum.equals(itemAddress.DBNum))
                || itemAddress.getSimaticDataType() == SimaticPLCDataTypes.BIT
                || this.address.getSimaticDataType() == SimaticPLCDataTypes.BIT
                || (itemAddress.addressByte > (this.address.addressByte + this.address.getDataLength()))
                || ((itemAddress.addressByte + itemAddress.getDataLength()) < this.address.addressByte)
                || (itemAddress.addressByte + itemAddress.getDataLength() - this.address.addressByte > MAX_DATA_LENGTH)
                || (this.address.addressByte + this.getAddressSpaceLength()
                        - itemAddress.addressByte > MAX_DATA_LENGTH);
    }

    public void insert(SimaticWriteDataArea data) {
        if (this.getStartAddress() > data.getStartAddress()) {
            // new length
            this.address.dataLength = (((this.getStartAddress()
                    + this.getAddressSpaceLength()) > (data.getStartAddress() + data.getAddressSpaceLength()))
                            ? (this.getStartAddress() + this.getAddressSpaceLength())
                            : (data.getStartAddress() + data.getAddressSpaceLength()))
                    - this.address.addressByte;
            // data array
            ByteBuffer newDataBuffer = ByteBuffer.wrap(new byte[this.address.dataLength]);
            // put new data
            newDataBuffer.put(data.getData());
            // and behind rest of old
            newDataBuffer.put(this.getData(),
                    data.getAddressSpaceLength() - (this.getStartAddress() - data.getStartAddress()),
                    this.getAddressSpaceLength()
                            - (data.getAddressSpaceLength() - (this.getStartAddress() - data.getStartAddress())));
            // new start address
            this.address.addressByte = data.getStartAddress();
            this.itemData = newDataBuffer.array();

        } else if (this.getStartAddress() < data.getStartAddress()) {
            if ((this.getStartAddress() + this.getAddressSpaceLength()) < (data.getStartAddress()
                    + data.getAddressSpaceLength())) {
                int oldDataLength = this.address.dataLength;
                this.address.dataLength += (data.getStartAddress() + data.getAddressSpaceLength())
                        - (this.getStartAddress() + this.getAddressSpaceLength());
                // data array
                byte[] newData = new byte[this.address.dataLength];
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
            int oldDataLength = this.address.dataLength;
            this.address.dataLength += data.getAddressSpaceLength() - this.getAddressSpaceLength();
            // data array
            byte[] newData = new byte[this.address.dataLength];
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
