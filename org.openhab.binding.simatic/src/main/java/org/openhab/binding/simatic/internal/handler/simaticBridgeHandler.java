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

import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simatic.internal.config.SimaticBridgeConfiguration;
import org.openhab.binding.simatic.internal.simatic.SimaticChannel;
import org.openhab.binding.simatic.internal.simatic.SimaticGenericDevice;
import org.openhab.binding.simatic.internal.simatic.SimaticTCP;
import org.openhab.binding.simatic.internal.simatic.SimaticTCP200;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link simaticHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author VitaTucek - Initial contribution
 */
/**
 * @author tucek
 *
 */
@NonNullByDefault
public class SimaticBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(SimaticBridgeHandler.class);

    private final int DEFAULT_SCANTIME = 5000;

    private @Nullable SimaticBridgeConfiguration config;

    public @Nullable SimaticGenericDevice connection = null;

    private @Nullable ScheduledFuture periodicJob;

    // devices
    // private Map<String, SimaticGenericDevice> devices = new HashMap<String, SimaticGenericDevice>();

    public SimaticBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        config = getConfigAs(SimaticBridgeConfiguration.class);

        var ip = config.ipAddress;

        if (ip == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "No IP address");
            return;
        }

        logger.debug("{} - Bridge configuration: IP={},Rack={},Slot={},Comm={},Is200={}", getThing().getLabel(), ip,
                config.rack, config.slot, config.communicationType, config.isS7200);

        // S7-200 PLC
        if (config.isS7200) {
            connection = new SimaticTCP200(ip, config.rack, config.slot);
        } else {
            connection = new SimaticTCP(ip, config.rack, config.slot, config.communicationType);
        }

        // react on connection changes
        connection.onConnectionChanged((connected) -> {
            if (connected) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        });

        // temporarily status
        updateStatus(ThingStatus.UNKNOWN);

        // background initialization:

        scheduler.execute(() -> {
            if (connection.open()) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }

            periodicJob = scheduler.scheduleAtFixedRate(() -> {
                execute();
            }, 0, DEFAULT_SCANTIME, TimeUnit.MILLISECONDS);
        });
    }

    /**
     * Called at specified period
     */
    @SuppressWarnings("null")
    protected void execute() {
        if (connection == null) {
            return;
        }
        // TODO: move into SimaticTCP / SimaticGenericDevice
        if (!connection.isConnected() || connection.shouldReconnect()) {
            connection.reconnectWithDelaying();
        }
        if (connection.isConnected()) {
            // check device for new data
            connection.checkNewData();
        }
    }

    @Override
    public void dispose() {
        if (connection != null) {
            connection.close();
            connection = null;
            if (periodicJob != null) {
                periodicJob.cancel(true);
            }
        }
        logger.debug("{} - bridge has been stopped", getThing().getLabel());
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // no command for bridge
    }

    /**
     * Update bridge configuration by all things channels
     */
    public void updateConfig() {
        int channelCount = 0;
        int stateChannelCount = 0;

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

        ArrayList<SimaticChannel> stateItems = new ArrayList<SimaticChannel>(stateChannelCount);

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
            connection.setDataAreas(stateItems);
        }

        logger.debug("{} - updating {} channels({} read)", getThing().getLabel(), channelCount, stateChannelCount);
    }
}
