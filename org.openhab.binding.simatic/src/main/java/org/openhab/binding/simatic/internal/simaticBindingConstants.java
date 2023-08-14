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
package org.openhab.binding.simatic.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.type.ChannelTypeUID;

/**
 * The {@link SimaticBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author VitaTucek - Initial contribution
 */
@NonNullByDefault
public class SimaticBindingConstants {

    public static final String VERSION = "4.0.1";

    private static final String BINDING_ID = "simatic";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "bridge");
    public static final ThingTypeUID THING_TYPE_GENERIC = new ThingTypeUID(BINDING_ID, "generic_device");

    // List of all Bridge Channel ids
    public static final String CHANNEL_TAG_COUNT = "chTagCountTypeID";
    public static final String CHANNEL_REQUESTS = "chRequestsTypeID";
    public static final String CHANNEL_BYTES = "chBytesTypeID";

    // List of all Property IDs
    public static final String PROPERTY_BINDING_VERSION = "bindingVersion";
    public static final String PROPERTY_PDU = "pdu";
    public static final String PROPERTY_AREAS_COUNT = "areasCount";
    public static final String PROPERTY_AREAS = "areas";
    public static final String PROPERTY_PLC_NAME = "plcName";
    public static final String PROPERTY_MODULE_NAME = "moduleName";
    public static final String PROPERTY_MODULE_NAME_TYPE = "moduleNameType";
    public static final String PROPERTY_COPYRIGHT = "copyright";
    public static final String PROPERTY_SERIAL = "serialNumber";
    public static final String PROPERTY_ORDER_NUMBER = "orderNumber";
    public static final String PROPERTY_HW_VERSION = "hardwareVersion";
    public static final String PROPERTY_FW_VERSION = "firmwareVersion";
    public static final String PROPERTY_MEMORY_SIZE = "workingMemorySize";

    // List of all Channel Type UIDs
    public static final ChannelTypeUID CHANNEL_TYPE_TAG_COUNT = new ChannelTypeUID(BINDING_ID, CHANNEL_TAG_COUNT);
    public static final ChannelTypeUID CHANNEL_TYPE_REQUESTS = new ChannelTypeUID(BINDING_ID, CHANNEL_REQUESTS);
    public static final ChannelTypeUID CHANNEL_TYPE_BYTES = new ChannelTypeUID(BINDING_ID, CHANNEL_BYTES);

    // List of all Thing Channel ids
    public static final String CHANNEL_NUMBER = "chNumber";
    public static final String CHANNEL_COLOR = "chColor";
    public static final String CHANNEL_STRING = "chString";
    public static final String CHANNEL_CONTACT = "chContact";
    public static final String CHANNEL_SWITCH = "chSwitch";
    public static final String CHANNEL_DIMMER = "chDimmer";
    public static final String CHANNEL_ROLLERSHUTTER = "chRollershutter";
}
