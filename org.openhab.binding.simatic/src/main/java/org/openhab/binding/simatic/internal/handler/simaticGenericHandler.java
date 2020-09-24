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

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simatic.internal.simatic.SimaticChannel;
import org.openhab.binding.simatic.internal.simatic.SimaticGenericDevice;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.thing.type.ChannelTypeUID;
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
public class SimaticGenericHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(SimaticGenericHandler.class);

    private @Nullable SimaticGenericDevice connection = null;
    private long errorSetTime = 0;

    public final Map<ChannelUID, SimaticChannel> channels = new LinkedHashMap<ChannelUID, SimaticChannel>();

    public SimaticGenericHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("{} - initialize. Channels count={}", thing.getLabel(), thing.getChannels().size());

        int errors = 0;

        // check configuration
        for (Channel channel : thing.getChannels()) {
            final ChannelUID channelUID = channel.getUID();
            final ChannelTypeUID channelTypeUID = channel.getChannelTypeUID();
            if (channelTypeUID == null) {
                errors++;
                logger.warn("{} - Channel {} has no type", thing.getLabel(), channel.getLabel());
                continue;
            }

            final SimaticChannel chConfig = channel.getConfiguration().as(SimaticChannel.class);
            chConfig.channelId = channelUID;
            chConfig.channelType = channelTypeUID;

            if (!chConfig.init(this)) {
                errors++;
                logger.warn("{} - channel configuration error {}, Error={}", thing.getLabel(), chConfig,
                        chConfig.getError());
                continue;
            }

            logger.debug("{} - channel added {}", thing.getLabel(), chConfig);

            channels.put(channelUID, chConfig);
        }

        var bridge = getBridge();

        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
        } else {
            // get connection and update status
            bridgeStatusChanged(bridge.getStatusInfo());
        }

        if (errors > 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Channel configuration error");
        }
        BridgeHandler handler;
        if (bridge != null && (handler = bridge.getHandler()) != null) {
            ((SimaticBridgeHandler) handler).updateConfig();
        }
    }

    @Override
    public void dispose() {
        for (SimaticChannel ch : channels.values()) {
            ch.clear();
        }
        channels.clear();
        connection = null;
        logger.debug("{} - device dispose", getThing().getLabel());
    }

    /**
     * Update thing status by bridge status. Status is also set during initialization
     *
     * @param bridgeStatusInfo Current bridge status
     */
    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            connection = null;
            return;
        }
        if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            connection = null;
            return;
        }

        SimaticBridgeHandler b = (SimaticBridgeHandler) (getBridge().getHandler());
        if (b == null) {
            logger.error("simaticBridgeHandler is null");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }

        // bridge is online take his connection
        connection = b.connection;

        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("{} - Command {}({}) for channel {}", thing.getLabel(), command, command.getClass(), channelUID);

        // get cached values
        if (command instanceof RefreshType) {
            logger.error("{} - command: RefreshType not implemented", thing.getLabel());
            // updateState(channelUID, value);
            return;
        }

        if (connection == null) {
            return;
        }

        if (!channels.containsKey(channelUID)) {
            logger.error("{} - command: Channel does not exists. ChannelUID={}", thing.getLabel(), channelUID);
            return;
        }
        SimaticChannel channel = channels.get(channelUID);

        connection.sendData(channel, command);
    }

    @Override
    public void handleRemoval() {
        super.handleRemoval();
    }

    @Override
    public void updateState(ChannelUID channelUID, State state) {
        super.updateState(channelUID, state);
    }

    @Override
    public void updateStatus(ThingStatus status) {
        super.updateStatus(status);
    }

    @Override
    public void updateStatus(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description) {
        super.updateStatus(status, statusDetail, description);
    }

    public void setError(String message) {
        errorSetTime = System.currentTimeMillis();
        var st = getThing().getStatusInfo();
        if (st.getStatus() == ThingStatus.OFFLINE && st.getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR
                && st.getDescription() != null && st.getDescription().equals(message)) {
            return;
        }

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
    }

    public void clearError() {
        // no error
        if (getThing().getStatus() == ThingStatus.ONLINE) {
            return;
        }

        // minimum error time left
        if (System.currentTimeMillis() - errorSetTime > 10000) {
            updateStatus(ThingStatus.ONLINE);
        }
    }
}
