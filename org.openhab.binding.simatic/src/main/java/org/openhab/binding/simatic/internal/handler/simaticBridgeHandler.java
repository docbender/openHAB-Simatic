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

import static org.openhab.binding.simatic.internal.simaticBindingConstants.VERSION;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simatic.internal.config.simaticBridgeConfiguration;
import org.openhab.binding.simatic.internal.simatic.SimaticGenericDevice;
import org.openhab.binding.simatic.internal.simatic.SimaticTCP;
import org.openhab.binding.simatic.internal.simatic.SimaticTCP200;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
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
public class simaticBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(simaticBridgeHandler.class);

    private final int DEFAULT_SCANTIME = 1000;

    private @Nullable simaticBridgeConfiguration config;

    public @Nullable SimaticGenericDevice connection = null;

    private @Nullable ScheduledFuture periodicJob;

    // devices
    // private Map<String, SimaticGenericDevice> devices = new HashMap<String, SimaticGenericDevice>();

    public simaticBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        logger.debug("Simatic binding (v.{}) bridge has been started .", VERSION);
        config = getConfigAs(simaticBridgeConfiguration.class);

        var ip = config.ipAddress;

        if (ip == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "No IP address");
            return;
        }

        // S7-200 PLC
        if (config.isS7200) {
            connection = new SimaticTCP("", ip, config.rack, config.slot, config.communicationType);
        } else {
            connection = new SimaticTCP200("", ip, config.rack, config.slot);
        }

        // temporarily status
        updateStatus(ThingStatus.UNKNOWN);

        // background initialization:
        scheduler.execute(() -> {
            if (connection.open()) {
                updateStatus(ThingStatus.ONLINE);

                periodicJob = scheduler.scheduleAtFixedRate(() -> {
                    execute();
                }, 0, DEFAULT_SCANTIME, TimeUnit.MILLISECONDS);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
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
        logger.debug("Simatic binding bridge has been stopped.");
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // no command for bridge
    }
}
