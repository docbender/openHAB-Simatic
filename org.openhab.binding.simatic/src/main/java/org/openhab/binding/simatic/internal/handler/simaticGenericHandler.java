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
import org.openhab.binding.simatic.internal.config.SimaticChannel;
import org.openhab.binding.simatic.internal.config.SimaticGenericConfiguration;
import org.openhab.binding.simatic.internal.simatic.SimaticGenericDevice;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
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

    private @Nullable SimaticGenericConfiguration config;
    private @Nullable SimaticGenericDevice connection = null;

    public final Map<ChannelUID, SimaticChannel> channels = new LinkedHashMap<ChannelUID, SimaticChannel>();

    public SimaticGenericHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(SimaticGenericConfiguration.class);

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

            if (!chConfig.init()) {
                errors++;
                logger.warn("{} - channel configuration error {}, Error={}", thing.getLabel(), chConfig,
                        chConfig.getError());
                continue;
            }

            logger.debug("{} - channel added {}", thing.getLabel(), chConfig);

            channels.put(chConfig.channelId, chConfig);
        }

        // get connection and update status
        bridgeStatusChanged(getBridge().getStatusInfo());

        if (errors > 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Channel configuration error");
        }
        //
        ((SimaticBridgeHandler) getBridge().getHandler()).updateConfig();
    }

    @Override
    public void dispose() {
        logger.debug("{} - device dispose", getThing().getLabel());
        connection = null;
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
        if (connection == null) {
            return;
        }

        logger.debug("{} - Command {} for channel {}", thing.getLabel(), command, channelUID);

        // get cached values
        if (command instanceof RefreshType) {

            // updateState(channelUID, value);
        }
    }
}
