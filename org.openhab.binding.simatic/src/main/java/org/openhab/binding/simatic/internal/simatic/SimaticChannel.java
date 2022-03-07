/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.simatic.internal.simatic;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simatic.internal.SimaticBindingConstants;
import org.openhab.binding.simatic.internal.handler.SimaticGenericHandler;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.State;
import org.openhab.core.types.util.UnitUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author VitaTucek - Initial contribution
 *
 */
public class SimaticChannel {
    private static final Logger logger = LoggerFactory.getLogger(SimaticChannel.class);

    /** Channel ID */
    private ChannelUID channelId;
    /** ChannelType ID */
    private ChannelTypeUID channelType;
    /** State string address */
    public String stateAddress;
    /** Command string address */
    public String commandAddress;
    /** Number value unit */
    public String unit;
    /** Stored state value */
    private State value;
    /** Channel configuration error */
    private String error;
    /** State Simatic address */
    private SimaticPLCAddress stateAddressPlc;
    /** Command Simatic address */
    private SimaticPLCAddress commandAddressPlc;
    /** Channel thing */
    private SimaticGenericHandler thing;
    /** Last value update */
    private long valueUpdateTime = 0;
    private boolean missingCommandReported = false;
    private Unit<?> unitInstance = null;
    private boolean unitExists = false;
    private boolean chContact, chColor, chDimmer, chNumber, chRollershutter, chString, chSwitch;

    final private static Pattern numberAddressPattern = Pattern.compile(
            "^(([IQAEM][BW])(\\d+))$|^(([IQAEM]D)(\\d+)(F?))$|^(DB(\\d+)\\.DB([BW])(\\d+))$|^(DB(\\d+)\\.DB(D)(\\d+)(F?))$|^(([IQAEM])(\\d+)\\.([0-7]))$|^(DB(\\d+)\\.DBX(\\d+)\\.([0-7]))$");
    final private static Pattern stringAddressPattern = Pattern
            .compile("^(([IQAEM]B)(\\d+)\\[(\\d+)\\])$|^(DB(\\d+)\\.DBB(\\d+)\\[(\\d+)\\])$");
    final private static Pattern switchAddressPattern = Pattern.compile(
            "^(([IQAEM]B)(\\d+))$|^(DB(\\d+)\\.DBB(\\d+))$|^(([IQAEM])(\\d+)\\.([0-7]))$|^(DB(\\d+)\\.DBX(\\d+)\\.([0-7]))$");
    final private static Pattern contactAddressPattern = Pattern.compile(
            "^(([IQAEM]B)(\\d+))$|^(DB(\\d+)\\.DBB(\\d+))$|^(([IQAEM])(\\d+)\\.([0-7]))$|^(DB(\\d+)\\.DBX(\\d+)\\.([0-7]))$");
    final private static Pattern dimmerAddressPattern = Pattern
            .compile("^(([IQAEM]B)(\\d+))$|^(DB(\\d+)\\.DBB(\\d+))$");
    final private static Pattern colorAddressPattern = Pattern.compile("^(([IQAEM]D)(\\d+))$|^(DB(\\d+)\\.DBD(\\d+))$");
    final private static Pattern rollershutterStateAddressPattern = Pattern
            .compile("^(([IQAEM]B)(\\d+))$|^(DB(\\d+)\\.DB(B)(\\d+))$");
    final private static Pattern rollershutterCommandAddressPattern = Pattern
            .compile("^(([IQAEM][BW])(\\d+))$|^(DB(\\d+)\\.DB([BW])(\\d+))$");

    @Override
    public String toString() {
        return String.format("ChID=%s,StateAddress=%s,CmdAddress=%s", channelId.getId(), stateAddress, commandAddress);
    }

    /**
     * Initialize channel from thing configuration
     *
     * @param handler Thing handler
     * @return True if initialization is OK. When initialization not succeed, reason can be obtain by getError()
     */
    public boolean init(SimaticGenericHandler handler) {
        missingCommandReported = false;
        if (handler == null) {
            error = "ThingHandler is null";
            return false;
        }

        thing = handler;

        if (channelId == null) {
            error = "ChannelID is null";
            return false;
        }
        if (channelType == null) {
            error = "ChannelType is null";
            return false;
        }
        if (stateAddress == null && commandAddress == null) {
            error = "No state or command address specified";
            return false;
        }

        if (stateAddress != null && (stateAddressPlc = checkAddress(stateAddress)) == null) {
            return false;
        }

        if (commandAddress != null && (commandAddressPlc = checkAddress(commandAddress, true)) == null) {
            return false;
        }

        if (unit != null) {
            unitInstance = UnitUtils.parseUnit(unit);
            if (unitInstance != null) {
                unitExists = true;
            } else {
                logger.warn("Channel {} - cannot parse defined unit({})", this.toString(), unit);
            }
        }

        return true;
    }

    /**
     * Clear instance
     */
    public void clear() {
        thing = null;
        value = null;
    }

    /**
     * Check string address obtained from configuration
     *
     * @param address Item address in Simatic syntax
     * @return
     */
    public @Nullable SimaticPLCAddress checkAddress(String address) {
        return checkAddress(address, false);
    }

    /**
     * Check string address obtained from configuration
     *
     * @param address Item address in Simatic syntax
     * @param useCommandPattern Use command pattern if exist
     * @return
     */
    public @Nullable SimaticPLCAddress checkAddress(String address, boolean useCommandPattern) {
        final Matcher matcher;
        switch (channelType.getId()) {
            case SimaticBindingConstants.CHANNEL_NUMBER:
                matcher = numberAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Supported types B,W,D. Address example IB10, MW100, DB1.DBD0, DB1.DBD0F",
                            address, channelType.getId());
                    return null;
                }
                if (matcher.group(1) != null) {
                    // memory area - byte, word
                    return new SimaticPLCAddress(matcher.group(2), Integer.parseInt(matcher.group(3)), false);
                } else if (matcher.group(4) != null) {
                    // memory area - dword
                    return new SimaticPLCAddress(matcher.group(5), Integer.parseInt(matcher.group(6)),
                            matcher.group(7) != null && !matcher.group(7).isEmpty());
                } else if (matcher.group(8) != null) {
                    // datablock area - byte, word
                    return new SimaticPLCAddress(Integer.parseInt(matcher.group(9)), matcher.group(10),
                            Integer.parseInt(matcher.group(11)), false);
                } else if (matcher.group(12) != null) {
                    // datablock area - dword
                    return new SimaticPLCAddress(Integer.parseInt(matcher.group(13)), matcher.group(14),
                            Integer.parseInt(matcher.group(15)),
                            matcher.group(16) != null && !matcher.group(16).isEmpty());
                } else if (matcher.group(17) != null) {
                    // memory area - bit
                    return new SimaticPLCAddress(matcher.group(18), Integer.parseInt(matcher.group(19)),
                            Integer.parseInt(matcher.group(20)));
                } else if (matcher.group(21) != null) {
                    // datablock area - bit
                    return new SimaticPLCAddress(Integer.parseInt(matcher.group(22)),
                            Integer.parseInt(matcher.group(23)), Integer.parseInt(matcher.group(24)));
                } else {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Supported types B,W,D. Address example IB10, MW100, DB1.DBD0, DB1.DBD0F",
                            address, channelType.getId());
                    return null;
                }
            case SimaticBindingConstants.CHANNEL_STRING:
                matcher = stringAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Supported types BYTE. Length must be specified. Address example MB100[16]",
                            address, channelType.getId());
                    return null;
                }
                if (matcher.group(1) == null) {
                    return new SimaticPLCAddress(Integer.parseInt(matcher.group(6)), Integer.parseInt(matcher.group(7)),
                            0, Integer.parseInt(matcher.group(8)));
                } else {
                    return new SimaticPLCAddress(matcher.group(2), Integer.parseInt(matcher.group(3)), 0,
                            Integer.parseInt(matcher.group(4)));
                }
            case SimaticBindingConstants.CHANNEL_SWITCH:
                matcher = switchAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Supported types BYTE, BIT. Address example MB100, M100.0",
                            address, channelType.getId());
                    return null;
                }
                if (matcher.group(1) != null) {
                    return new SimaticPLCAddress(matcher.group(2), Integer.parseInt(matcher.group(3)));
                } else if (matcher.group(4) != null) {
                    return new SimaticPLCAddress(Integer.parseInt(matcher.group(5)), "B",
                            Integer.parseInt(matcher.group(6)));
                } else if (matcher.group(7) != null) {
                    return new SimaticPLCAddress(matcher.group(8), Integer.parseInt(matcher.group(9)),
                            Integer.parseInt(matcher.group(10)));
                } else {
                    return new SimaticPLCAddress(Integer.parseInt(matcher.group(12)),
                            Integer.parseInt(matcher.group(13)), Integer.parseInt(matcher.group(14)));
                }
            case SimaticBindingConstants.CHANNEL_CONTACT:
                matcher = contactAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Supported types BYTE, BIT. Address example MB100, M100.0",
                            address, channelType.getId());
                    return null;
                }
                if (matcher.group(1) != null) {
                    return new SimaticPLCAddress(matcher.group(2), Integer.parseInt(matcher.group(3)));
                } else if (matcher.group(4) != null) {
                    return new SimaticPLCAddress(Integer.parseInt(matcher.group(5)), "B",
                            Integer.parseInt(matcher.group(6)));
                } else if (matcher.group(7) != null) {
                    return new SimaticPLCAddress(matcher.group(8), Integer.parseInt(matcher.group(9)),
                            Integer.parseInt(matcher.group(10)));
                } else {
                    return new SimaticPLCAddress(Integer.parseInt(matcher.group(12)),
                            Integer.parseInt(matcher.group(13)), Integer.parseInt(matcher.group(14)));
                }
            case SimaticBindingConstants.CHANNEL_DIMMER:
                matcher = dimmerAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Supported types BYTE. Address example MB100",
                            address, channelType.getId());
                    return null;
                }
                if (matcher.group(1) == null) {
                    return new SimaticPLCAddress(Integer.parseInt(matcher.group(5)), "B",
                            Integer.parseInt(matcher.group(6)));
                } else {
                    return new SimaticPLCAddress(matcher.group(2), Integer.parseInt(matcher.group(3)));
                }
            case SimaticBindingConstants.CHANNEL_COLOR:
                matcher = colorAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Supported types DWORD. Address example MD100",
                            address, channelType.getId());
                    return null;
                }
                if (matcher.group(1) == null) {
                    return new SimaticPLCAddress(Integer.parseInt(matcher.group(5)), "D",
                            Integer.parseInt(matcher.group(6)));
                } else {
                    return new SimaticPLCAddress(matcher.group(2), Integer.parseInt(matcher.group(3)));
                }
            case SimaticBindingConstants.CHANNEL_ROLLERSHUTTER:
                if (useCommandPattern) {
                    matcher = rollershutterCommandAddressPattern.matcher(address);
                } else {
                    matcher = rollershutterStateAddressPattern.matcher(address);
                }
                if (!matcher.matches()) {
                    if (useCommandPattern) {
                        error = String.format(
                                "Unsupported address '%s' for typeID=%s. Supported types BYTE, WORD. Address example MB100",
                                address, channelType.getId());
                    } else {
                        error = String.format(
                                "Unsupported address '%s' for typeID=%s. Supported types BYTE. Address example MB100",
                                address, channelType.getId());
                    }
                    return null;
                }
                if (matcher.group(1) == null) {
                    return new SimaticPLCAddress(Integer.parseInt(matcher.group(5)), matcher.group(6),
                            Integer.parseInt(matcher.group(7)));
                } else {
                    return new SimaticPLCAddress(matcher.group(2), Integer.parseInt(matcher.group(3)));
                }
            default:
                error = String.format("Unsupported channel type for address %s. TypeID=%s", address,
                        channelType.getId());
                return null;
        }
    }

    /**
     * Get error if init() failed
     *
     * @return
     */
    public @Nullable String getError() {
        return error;
    }

    /**
     * Get address for channel state
     *
     * @return
     */
    public @Nullable SimaticPLCAddress getStateAddress() {
        return stateAddressPlc;
    }

    /**
     * Get address for command
     *
     * @return
     */
    public @Nullable SimaticPLCAddress getCommandAddress() {
        return commandAddressPlc;
    }

    /**
     * Get number value unit
     *
     * @return Unit
     */
    public Unit<?> getUnit() {
        return unitInstance;
    }

    /**
     * Check if unit presented
     *
     * @return Unit exists flag
     */
    public boolean hasUnit() {
        return unitExists;
    }

    /**
     * Set state from incoming data
     *
     * @param buffer Incoming data
     * @param position Data start position in buffer
     */
    public void setState(byte[] buffer, int start) {
        // logger.debug("item={}", toString());
        // logger.debug("buffer={}", buffer.length);
        // logger.debug("position={}", position);
        // logger.debug("item len={}", getStateAddress().getDataLength());

        int position = getStateAddress().getByteOffset() - start;

        try {
            ByteBuffer bb = ByteBuffer.wrap(buffer, position, getStateAddress().getDataLength());
            bb.order(ByteOrder.BIG_ENDIAN);

            if (isString()) {
                // check for '\0' char and resolve string length
                int i;
                for (i = position; i < getStateAddress().getDataLength(); i++) {
                    if (buffer[i] == 0) {
                        break;
                    }
                }
                String str = new String(buffer, position, i, thing.getCharset());
                setState(new StringType(str));
            } else if (isNumber()) {
                if (getStateAddress().isFloat()) {
                    setState(hasUnit() ? new QuantityType<>(bb.getFloat(), getUnit())
                            : new DecimalType((Number) bb.getFloat()));
                } else {
                    final int intValue;
                    if (getStateAddress().getSimaticDataType() == SimaticPLCDataTypes.BIT) {
                        intValue = (bb.get() & (int) Math.pow(2, getStateAddress().getBitOffset())) != 0 ? 1 : 0;
                    } else if (getStateAddress().getSimaticDataType() == SimaticPLCDataTypes.BYTE) {
                        intValue = bb.get();
                    } else if (getStateAddress().getSimaticDataType() == SimaticPLCDataTypes.WORD) {
                        intValue = bb.getShort();
                    } else if (getStateAddress().getSimaticDataType() == SimaticPLCDataTypes.DWORD) {
                        intValue = bb.getInt();
                    } else {
                        intValue = 0;
                    }

                    setState(hasUnit() ? new QuantityType<>(intValue, getUnit()) : new DecimalType((Number) intValue));
                }
            } else if (isDimmer()) {
                setState(new PercentType(bb.get()));
            } else if (isContact()) {
                if (getStateAddress().getSimaticDataType() == SimaticPLCDataTypes.BIT) {
                    setState((bb.get() & (int) Math.pow(2, getStateAddress().getBitOffset())) != 0 ? OpenClosedType.OPEN
                            : OpenClosedType.CLOSED);
                } else {
                    setState((bb.get() != 0) ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                }
            } else if (isSwitch()) {
                if (getStateAddress().getSimaticDataType() == SimaticPLCDataTypes.BIT) {
                    setState((bb.get() & (int) Math.pow(2, getStateAddress().getBitOffset())) != 0 ? OnOffType.ON
                            : OnOffType.OFF);
                } else {
                    setState(bb.get() != 0 ? OnOffType.ON : OnOffType.OFF);
                }
            } else if (isRollershutter()) {
                setState(new PercentType(bb.get()));
            } else if (isColor()) {
                byte b0 = bb.get();
                byte b1 = bb.get();
                byte b2 = bb.get();
                // byte b3 = bb.get();

                setState(HSBType.fromRGB(b0 & 0xFF, b1 & 0xFF, b2 & 0xFF));
            } else {
                setState(null);
                logger.warn("{} - Incoming data channel {} - Unsupported channel type {}.", toString(), channelId,
                        channelType.getId());
                return;
            }
        } catch (Exception ex) {
            logger.error("{} - Incoming data post error. Item:{}", toString(), channelId, ex);
        }

        /*
         * if (logger.isTraceEnabled()) {
         * logger.trace("{} - Incoming data - item:{}/state:{}", toString(), channelId, getState());
         * }
         */
    }

    /**
     * Set last channel state
     *
     * @param state
     */
    public void setState(State state) {
        // not thing no update
        if (thing == null) {
            return;
        }
        // clear thing errors
        clearError();
        // in OnChange mode, only changed values are forwarded
        if (thing.getUpdateMode() == SimaticUpdateMode.OnChange && value != null && value.equals(state)) {
            return;
        }
        // store value internally
        value = state;
        // sent value into OH core
        thing.updateState(channelId, state);
        // mark update time
        setValueUpdateTime(System.currentTimeMillis());
    }

    /**
     * Get last channel state
     *
     * @return
     */
    public @Nullable State getState() {
        return value;
    }

    /**
     * Get channel thing
     *
     * @return
     */
    public @Nullable SimaticGenericHandler getThing() {
        return thing;
    }

    /**
     * Set configuration error message
     *
     * @param message
     */
    public void setError(String message) {
        if (thing == null) {
            return;
        }
        thing.setError(message);
    }

    /**
     * Clear error in parent
     */
    private void clearError() {
        if (thing == null) {
            return;
        }

        thing.clearError();
    }

    /**
     * Get last value time
     *
     * @return
     */
    public long getValueUpdateTime() {
        return valueUpdateTime;
    }

    /**
     * Set last value time
     *
     * @param valueUpdateTime
     */
    public void setValueUpdateTime(long valueUpdateTime) {
        this.valueUpdateTime = valueUpdateTime;
    }

    public boolean isMissingCommandReported() {
        if (!missingCommandReported) {
            missingCommandReported = true;
            return false;
        }
        return true;
    }

    /**
     * Set channel ID
     *
     * @param channelUID ChannelUID
     */
    public void setChannelId(ChannelUID channelUID) {
        this.channelId = channelUID;
    }

    /**
     * Set channel type
     *
     * @param channelTypeUID ChannelTypeUID
     */
    public void setChannelType(ChannelTypeUID channelTypeUID) {
        this.channelType = channelTypeUID;

        chContact = chColor = chDimmer = chNumber = chRollershutter = chString = chSwitch = false;
        switch (channelType.getId()) {
            case SimaticBindingConstants.CHANNEL_CONTACT:
                chContact = true;
                break;
            case SimaticBindingConstants.CHANNEL_COLOR:
                chColor = true;
                break;
            case SimaticBindingConstants.CHANNEL_DIMMER:
                chDimmer = true;
                break;
            case SimaticBindingConstants.CHANNEL_NUMBER:
                chNumber = true;
                break;
            case SimaticBindingConstants.CHANNEL_ROLLERSHUTTER:
                chRollershutter = true;
                break;
            case SimaticBindingConstants.CHANNEL_STRING:
                chString = true;
                break;
            case SimaticBindingConstants.CHANNEL_SWITCH:
                chSwitch = true;
                break;
        }
    }

    /**
     * Get channel ID
     */
    public ChannelUID getChannelId() {
        return channelId;
    }

    /**
     * Get channel type
     */
    public ChannelTypeUID getChannelType() {
        return channelType;
    }

    /**
     * Check Contact channel type
     *
     * @return True if channel has that type
     */
    public boolean isContact() {
        return chContact;
    }

    /**
     * Check Color channel type
     *
     * @return True if channel has that type
     */
    public boolean isColor() {
        return chColor;
    }

    /**
     * Check Dimmer channel type
     *
     * @return True if channel has that type
     */
    public boolean isDimmer() {
        return chDimmer;
    }

    /**
     * Check Number channel type
     *
     * @return True if channel has that type
     */
    public boolean isNumber() {
        return chNumber;
    }

    /**
     * Check Rollershutter channel type
     *
     * @return True if channel has that type
     */
    public boolean isRollershutter() {
        return chRollershutter;
    }

    /**
     * Check String channel type
     *
     * @return True if channel has that type
     */
    public boolean isString() {
        return chString;
    }

    /**
     * Check Switch channel type
     *
     * @return True if channel has that type
     */
    public boolean isSwitch() {
        return chSwitch;
    }
}