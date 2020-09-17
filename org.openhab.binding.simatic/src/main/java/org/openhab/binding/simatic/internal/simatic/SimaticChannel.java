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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simatic.internal.SimaticBindingConstants;
import org.openhab.binding.simatic.internal.handler.SimaticGenericHandler;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author VitaTucek - Initial contribution
 *
 */
public class SimaticChannel {
    private static final Logger logger = LoggerFactory.getLogger(SimaticChannel.class);

    public ChannelUID channelId;
    public ChannelTypeUID channelType;
    public String stateAddress;
    public String commandAddress;
    private State value;
    private String error;
    private SimaticPLCAddress stateAddressPlc;
    private SimaticPLCAddress commandAddressPlc;
    private SimaticGenericHandler thing;
    private long valueUpdateTime = 0;

    final private static Pattern numberAddressPattern = Pattern
            .compile("^(([IQAEM][BWD])(\\d+)(F?))$|^(DB(\\d+)\\.DB([BWD])(\\d+)(F?))$");
    final private static Pattern stringAddressPattern = Pattern
            .compile("^(([IQAEM]B)(\\d+)\\[(\\d+)\\])$|^(DB(\\d+)\\.DBB(\\d+)\\[(\\d+)\\])$");
    final private static Pattern switchAddressPattern = Pattern.compile(
            "^(([IQAEM]B)(\\d+))$|^(DB(\\d+)\\.DBB(\\d+))$|^(([IQAEM])(\\d+)\\.([0-7]))$|^(DB(\\d+)\\.DBX(\\d+)\\.([0-7]))$");
    final private static Pattern contactAddressPattern = Pattern.compile(
            "^(([IQAEM]B)(\\d+))$|^(DB(\\d+)\\.DBB(\\d+))$|^(([IQAEM])(\\d+)\\.([0-7]))$|^(DB(\\d+)\\.DBX(\\d+)\\.([0-7]))$");
    final private static Pattern dimmerAddressPattern = Pattern
            .compile("^(([IQAEM]B)(\\d+))$|^(DB(\\d+)\\.DBB(\\d+))$");
    final private static Pattern colorAddressPattern = Pattern.compile("^(([IQAEM]D)(\\d+))$|^(DB(\\d+)\\.DBD(\\d+))$");
    final private static Pattern rollershutterAddressPattern = Pattern
            .compile("^(([IQAEM]B)(\\d+))$|^(DB(\\d+)\\.DBB(\\d+))$");

    @Override
    public String toString() {
        return String.format("ChID=%s,StateAddress=%s,CmdAddress=%s", channelId.getId(), stateAddress, commandAddress);
    }

    public boolean init(SimaticGenericHandler handler) {
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

        if (commandAddress != null && (commandAddressPlc = checkAddress(commandAddress)) == null) {
            return false;
        }

        return true;
    }

    public void clear() {
        thing = null;
        value = null;
    }

    public @Nullable SimaticPLCAddress checkAddress(String address) {
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
                if (matcher.group(1) == null) {
                    return new SimaticPLCAddress(Integer.parseInt(matcher.group(6)), matcher.group(7),
                            Integer.parseInt(matcher.group(8)),
                            matcher.group(9) != null && !matcher.group(9).isEmpty());
                } else {
                    return new SimaticPLCAddress(matcher.group(2), Integer.parseInt(matcher.group(3)),
                            matcher.group(4) != null && !matcher.group(4).isEmpty());
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
                matcher = rollershutterAddressPattern.matcher(address);
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
            default:
                return null;
        }
    }

    public @Nullable String getError() {
        return error;
    }

    public @Nullable SimaticPLCAddress getStateAddress() {
        return stateAddressPlc;
    }

    public @Nullable SimaticPLCAddress getCommandAddress() {
        return commandAddressPlc;
    }

    public void setState(State state) {
        value = state;
        if (thing == null) {
            return;
        }
        thing.updateState(channelId, state);
        valueUpdateTime = System.currentTimeMillis();
        clearError();
    }

    public @Nullable State getState() {
        return value;
    }

    public @Nullable SimaticGenericHandler getThing() {
        return thing;
    }

    public void setError(String message) {
        if (thing == null) {
            return;
        }
        thing.setError(message);
    }

    private void clearError() {
        if (thing == null) {
            return;
        }

        thing.clearError();
    }
}