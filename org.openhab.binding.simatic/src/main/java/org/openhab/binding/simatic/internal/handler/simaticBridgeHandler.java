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
package org.openhab.binding.simatic.internal.handler;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simatic.internal.SimaticBindingConstants;
import org.openhab.binding.simatic.internal.config.SimaticBridgeConfiguration;
import org.openhab.binding.simatic.internal.simatic.SimaticChannel;
import org.openhab.binding.simatic.internal.simatic.SimaticGenericDevice;
import org.openhab.binding.simatic.internal.simatic.SimaticTCP;
import org.openhab.binding.simatic.internal.simatic.SimaticTCP200;
import org.openhab.binding.simatic.internal.simatic.SimaticUpdateMode;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link simaticHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author VitaTucek - Initial contribution
 */
@NonNullByDefault
public class SimaticBridgeHandler extends BaseBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(SimaticBridgeHandler.class);

    private @Nullable SimaticBridgeConfiguration config;

    public @Nullable SimaticGenericDevice connection = null;

    // bridge channels
    private @Nullable ChannelUID chTagCount, chRequests, chBytes;

    private int channelCount = 0;
    /** Initial scheduler delay */
    private static final long INIT_SECONDS = 5;

    /**
     * Constructor
     *
     * @param bridge
     */
    @SuppressWarnings("null")
    public SimaticBridgeHandler(Bridge bridge) {
        super(bridge);

        // retrieve bridge channels
        getThing().getChannels().forEach((channel) -> {
            if (channel.getChannelTypeUID().equals(SimaticBindingConstants.CHANNEL_TYPE_TAG_COUNT)) {
                chTagCount = channel.getUID();
            } else if (channel.getChannelTypeUID().equals(SimaticBindingConstants.CHANNEL_TYPE_REQUESTS)) {
                chRequests = channel.getUID();
            } else if (channel.getChannelTypeUID().equals(SimaticBindingConstants.CHANNEL_TYPE_BYTES)) {
                chBytes = channel.getUID();
            }
        });
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        updateProperty(SimaticBindingConstants.PROPERTY_BINDING_VERSION, SimaticBindingConstants.VERSION);

        config = getConfigAs(SimaticBridgeConfiguration.class);

        logger.debug(
                "{} - Bridge configuration: Host/IP={},Rack={},Slot={},Comm={},Is200={},Charset={},PollRate={},Mode={}",
                getThing().getLabel(), config.address, config.rack, config.slot, config.communicationType,
                config.isS7200, config.charset, config.pollRate, config.updateMode);

        // configuration validation
        boolean valid = true;

        if (config.address == null || config.address.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "No Host/IP address");
            valid = false;
        }

        if (valid && (config.rack < 0 || config.rack > 2)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid Rack number. Valid is 0-2.");
            valid = false;
        }

        if (valid && (config.slot < 0 || config.slot > 15)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid Slot number. Valid is 0-15.");
            valid = false;
        }

        if (valid && (config.communicationType == null || !(config.communicationType.equals("S7")
                || config.communicationType.equals("PG") || config.communicationType.equals("OP")))) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid communication type.");
            valid = false;
        }

        if (config.pollRate <= 0) {
            logger.warn(
                    "{} - poll rate is set to 0. That means new data will be read immediately after previous one will be finished.",
                    getThing().getLabel());
        }

        if (valid && (config.updateMode == null || !SimaticUpdateMode.validate(config.updateMode))) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid value update mode.");
            valid = false;
        }

        if (!valid) {
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
            logger.error("{} - Bridge configuration is invalid. Host/IP={},Rack={},Slot={},Comm={},Is200={},Mode={}",
                    getThing().getLabel(), config.address, config.rack, config.slot, config.communicationType,
                    config.isS7200, config.updateMode);
            return;
        }

        Charset charset;
        if (config.charset == null || config.charset.isBlank()) {
            charset = Charset.defaultCharset();
        } else if (!Charset.isSupported(config.charset)) {
            charset = Charset.defaultCharset();
            logger.warn("{} - charset '{}' is not recognized. Default one is used.", getThing().getLabel(),
                    config.charset);
        } else {
            charset = Charset.forName(config.charset);
        }

        logger.info("{} - Current charset {}", getThing().getLabel(), charset.name());

        // S7-200 PLC
        if (config.isS7200) {
            connection = new SimaticTCP200(config.address, config.rack, config.slot, config.pollRate, charset,
                    SimaticUpdateMode.fromString(config.updateMode));
        } else {
            connection = new SimaticTCP(config.address, config.rack, config.slot, config.communicationType,
                    config.pollRate, charset, SimaticUpdateMode.fromString(config.updateMode));
        }

        // react on connection changes
        connection.onConnectionChanged((connected) -> {
            if (connected) {
                updateProperty(SimaticBindingConstants.PROPERTY_PDU, String.valueOf(connection.getPduSize()));
                updateProperty(SimaticBindingConstants.PROPERTY_AREAS_COUNT,
                        String.valueOf(connection.getReadAreas().size()));
                updateProperty(SimaticBindingConstants.PROPERTY_AREAS,
                        (connection.getReadAreas().size() == 0) ? "none" : connection.getReadAreas().toString());
                if (connection.info.getPlcName() != null) {
                    updateProperty(SimaticBindingConstants.PROPERTY_PLC_NAME, connection.info.getPlcName());
                }
                if (connection.info.getModuleName() != null) {
                    updateProperty(SimaticBindingConstants.PROPERTY_MODULE_NAME, connection.info.getModuleName());
                }
                if (connection.info.getModuleTypeName() != null) {
                    updateProperty(SimaticBindingConstants.PROPERTY_MODULE_NAME_TYPE,
                            connection.info.getModuleTypeName());
                }
                if (connection.info.getCopyright() != null) {
                    updateProperty(SimaticBindingConstants.PROPERTY_COPYRIGHT, connection.info.getCopyright());
                }
                if (connection.info.getSerialNumber() != null) {
                    updateProperty(SimaticBindingConstants.PROPERTY_SERIAL, connection.info.getSerialNumber());
                }
                if (connection.info.getOrderNr() != null) {
                    updateProperty(SimaticBindingConstants.PROPERTY_ORDER_NUMBER, connection.info.getOrderNr());
                }
                if (connection.info.getHwVersion() != null) {
                    updateProperty(SimaticBindingConstants.PROPERTY_HW_VERSION, connection.info.getHwVersion());
                }
                if (connection.info.getFwVersion() != null) {
                    updateProperty(SimaticBindingConstants.PROPERTY_FW_VERSION, connection.info.getFwVersion());
                }
                if (connection.info.getMemorySize() != null) {
                    updateProperty(SimaticBindingConstants.PROPERTY_MEMORY_SIZE, connection.info.getMemorySize());
                }

                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        });

        connection.onMetricsUpdated((requests, bytes) -> {
            updateState(chRequests, new DecimalType(requests));
            updateState(chBytes, new DecimalType(bytes));
        });

        // temporarily status
        updateStatus(ThingStatus.UNKNOWN);

        // background initialization
        scheduler.schedule(() -> {
            if (!connection.open()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        }, INIT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    protected void updateState(@Nullable ChannelUID channel, State state) {
        if (channel == null) {
            logger.debug("{} - updateState(...) channelID is null for state={}", getThing().getLabel(), state);
            return;
        }
        // logger.debug("{} - update channelID={}, state={}", getThing().getLabel(), channel, state);

        super.updateState(channel, state);
    }

    @Override
    public void dispose() {
        if (connection != null) {
            connection.dispose();
            connection = null;
        }
        logger.debug("{} - bridge has been stopped", getThing().getLabel());
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("{} - Command {} for channel {}", thing.getLabel(), command, channelUID);

        // get cached values
        if (command instanceof RefreshType) {
            if (channelUID.equals(chTagCount)) {
                updateState(channelUID, new DecimalType(channelCount));
            }
        }
    }

    @Override
    public void handleRemoval() {
        super.handleRemoval();
    }

    /**
     * Update bridge configuration by all things channels
     */
    public void updateConfig() {
        int stateChannelCount = 0;
        channelCount = 0;

        for (Thing th : getThing().getThings()) {
            var h = ((SimaticGenericHandler) th.getHandler());
            if (h == null) {
                continue;
            }
            channelCount += h.channels.size();
            for (SimaticChannel ch : h.channels.values()) {
                if (ch.getStateAddress() != null) {
                    stateChannelCount++;
                }
            }
        }

        var stateItems = new ArrayList<SimaticChannel>(stateChannelCount);

        for (Thing th : getThing().getThings()) {
            var h = ((SimaticGenericHandler) th.getHandler());
            if (h == null) {
                continue;
            }
            for (SimaticChannel ch : h.channels.values()) {
                if (ch.getStateAddress() != null) {
                    stateItems.add(ch);
                }
            }
        }

        if (connection != null) {
            var c = connection;
            c.setDataAreas(stateItems);
        }

        updateState(chTagCount, new DecimalType(channelCount));

        logger.debug("{} - updating {} channels({} read)", getThing().getLabel(), channelCount, stateChannelCount);
    }
}
