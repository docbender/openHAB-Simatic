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

import static org.openhab.binding.simatic.internal.SimaticBindingConstants.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SimaticHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author VitaTucek - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.simatic", service = ThingHandlerFactory.class)
public class SimaticHandlerFactory extends BaseThingHandlerFactory {
    private final Logger logger = LoggerFactory.getLogger(SimaticHandlerFactory.class);

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_BRIDGE, THING_TYPE_GENERIC);
    protected final Set<SimaticBridgeHandler> bridges = Collections
            .synchronizedSet(Collections.newSetFromMap(new HashMap<>()));

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        final ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_BRIDGE.equals(thingTypeUID)) {
            var bridge = new SimaticBridgeHandler((Bridge) thing);
            bridges.add(bridge);
            return bridge;
        } else if (THING_TYPE_GENERIC.equals(thingTypeUID)) {
            return new SimaticGenericHandler(thing);
        } else {
            throw new IllegalStateException("Unsupported Thing type " + thingTypeUID.toString());
        }
    }

    @Activate
    @Override
    protected void activate(ComponentContext componentContext) {
        super.activate(componentContext);
        logger.info("Simatic binding (v.{}) has been started.", VERSION);
    }

    @Override
    protected void deactivate(ComponentContext componentContext) {
        super.deactivate(componentContext);
    }
}
