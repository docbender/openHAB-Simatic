/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openhab.binding.simatic.SimaticBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.ColorItem;
import org.openhab.core.library.items.ContactItem;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.RollershutterItem;
import org.openhab.core.library.items.StringItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for parsing the binding configuration.
 *
 * @author VitaTucek
 * @since 1.9.0
 */
public class SimaticGenericBindingProvider extends AbstractGenericBindingProvider implements SimaticBindingProvider {

    private static final Logger logger = LoggerFactory.getLogger(SimaticGenericBindingProvider.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBindingType() {
        return "simatic";
    }

    /**
     * Return all configs map
     *
     * @return
     */
    public Map<String, BindingConfig> configs() {
        return bindingConfigs;
    }

    /**
     * Return item config
     *
     * @param itemName
     *            Item name
     * @return
     */
    public BindingConfig getItemConfig(String itemName) {

        return bindingConfigs.get(itemName);
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
        // if (!(item instanceof SwitchItem || item instanceof DimmerItem)) {
        // throw new BindingConfigParseException("item '" + item.getName()
        // + "' is of type '" + item.getClass().getSimpleName()
        // + "', only Switch- and DimmerItems are allowed - please check your *.items configuration");
        // }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processBindingConfiguration(String context, Item item, String bindingConfig)
            throws BindingConfigParseException {
        if (logger.isDebugEnabled()) {
            logger.debug("processBindingConfiguration() method is called!");
            logger.debug("Item:" + item + "/Config:" + bindingConfig);
        }

        super.processBindingConfiguration(context, item, bindingConfig);

        BindingConfig commonConfig = null;

        // config
        //
        // device:busAddress:address/ID:type:direction
        //
        // device - target device/port (ex. "port" , "port1", "port2")
        // device:busAddress - bus / device address (0-127)
        // address/ID - number
        // type - byte or word or dword or array[length] or rgb or rgbw or hsb
        // direction - "I" or "O" or "IO"
        //
        // Matcher matcher =
        // Pattern.compile("^(port\\d*):(\\d+):(\\d+):([a-z0-9\\[\\]]+):(I|O|IO)$").matcher(bindingConfig);
        Matcher matcher = Pattern.compile("^(plc\\d*):([I|Q|A|E|M|D|B|X|W|D0-9.]+)((:[a-zA-Z0-9_]*)*)$")
                .matcher(bindingConfig);

        if (!matcher.matches()) {
            // look for info config
            matcher = Pattern.compile("^(plc\\d*):info:((state)|(previous_state)|(state_change_time)|(packet_lost))$")
                    .matcher(bindingConfig);

            if (!matcher.matches()) {
                throw new BindingConfigParseException("Illegal config format: " + bindingConfig
                        + ". Correct format: simatic=\"plc:itemAddress:dataType:ioDirection\". Example: simatic=\"plc:MB0:byte:O\"");
            } else {
                // device info config
                SimaticInfoBindingConfig config = new SimaticInfoBindingConfig();
                commonConfig = config;

                config.item = item;
                config.device = matcher.group(1);

                String param = matcher.group(3);

                if (param.equalsIgnoreCase("state")) {
                    config.infoType = InfoType.STATE;
                } else if (param.equalsIgnoreCase("previous_state")) {
                    config.infoType = InfoType.PREVIOUS_STATE;
                } else if (param.equalsIgnoreCase("state_change_time")) {
                    config.infoType = InfoType.STATE_CHANGE_TIME;
                } else if (param.equalsIgnoreCase("packet_lost")) {
                    config.infoType = InfoType.PACKET_LOST;
                } else {
                    throw new BindingConfigParseException("Unsupported info parameter " + param);
                }
            }
        } else {
            SimaticBindingConfig config;
            String device = matcher.group(1);
            String address = matcher.group(2);
            SimaticTypes dataType;
            int direction;

            if (!SimaticPLCAddress.ValidateAddress(address)) {
                throw new BindingConfigParseException("Invalid plc address: " + address);
            }

            // check if optional parameters are specified
            if (matcher.group(3).length() > 0) {
                dataType = resolveConfigDataType(matcher.group(3), item);
                direction = resolveConfigDirection(matcher.group(3));

                if (dataType == null) {
                    dataType = resolveDataTypeFromItemType(item, address);
                }

                if (dataType == SimaticTypes.ARRAY) {
                    config = new SimaticBindingConfig(item, device, address, dataType, direction,
                            resolveConfigDataLength(matcher.group(3), item));
                } else {
                    config = new SimaticBindingConfig(item, device, address, dataType, direction);
                }
            } else {
                dataType = resolveDataTypeFromItemType(item, address);

                config = new SimaticBindingConfig(item, device, address, dataType);
            }

            commonConfig = config;
        }

        if (logger.isDebugEnabled()) {
            logger.debug(commonConfig.toString());
        }

        addBindingConfig(item, commonConfig);
    }

    int resolveConfigDirection(String configParameters) {
        String[] optionalConfigs = configParameters.substring(1).split(":");

        for (int i = 0; i < optionalConfigs.length; i++) {
            String param = optionalConfigs[i].toLowerCase();
            // is direction?
            if (param.equals("i") || param.equals("o") || param.equals("io")) {
                return param.equals("io") ? 0 : param.equals("i") ? 1 : 2;
            }
        }

        return 0;
    }

    int resolveConfigDataLength(String configParameters, Item item) throws BindingConfigParseException {
        String[] optionalConfigs = configParameters.substring(1).split(":");

        for (int i = 0; i < optionalConfigs.length; i++) {
            String param = optionalConfigs[i].toLowerCase();
            // is datatype?
            if (!(param.equals("i") || param.equals("o") || param.equals("io"))) {

                Matcher matcher = Pattern.compile("^array\\[\\d+\\]$").matcher(param);

                if (matcher.matches()) {
                    if (item.getClass().isAssignableFrom(StringItem.class)) {
                        if (!param.startsWith("array")) {
                            logger.warn(
                                    "Item %s support datatype array only. Type %s is ignored. Setted to ARRAY with length 32.",
                                    item.getName(), param);
                            return 32;
                        } else {
                            return Integer.valueOf(matcher.group(1)).intValue();
                        }
                    }
                }
            }
        }

        return 1;
    }

    SimaticTypes resolveConfigDataType(String configParameters, Item item) throws BindingConfigParseException {
        String[] optionalConfigs = configParameters.substring(1).split(":");

        for (int i = 0; i < optionalConfigs.length; i++) {
            String param = optionalConfigs[i].toLowerCase();
            // is datatype?
            if (!(param.equals("i") || param.equals("o") || param.equals("io"))) {

                Matcher matcher = Pattern.compile("^byte|word|dword|float|hsb|rgb|rgbw|array\\[\\d+\\]$")
                        .matcher(param);

                if (matcher.matches()) {

                    if (item.getClass().isAssignableFrom(NumberItem.class)) {
                        if (!param.equals("byte") && !param.equals("word") && !param.equals("dword")
                                && !param.equals("float")) {
                            logger.warn(
                                    "Item %s supported datatypes: byte, word, dword or float. Type %s is ignored. Setted to word.",
                                    item.getName(), param);
                            return null;
                        } else {
                            return SimaticBindingConfig.getDataType(param);
                        }

                    } else if (item.getClass().isAssignableFrom(SwitchItem.class)) {
                        if (!param.equals("byte")) {
                            logger.warn("Item %s support datatype byte only. Type %s is ignored.", item.getName(),
                                    param);
                        }
                        return null;

                    } else if (item.getClass().isAssignableFrom(DimmerItem.class)) {
                        if (!param.equals("byte")) {
                            logger.warn("Item %s support datatype byte only. Type %s is ignored.", item.getName(),
                                    param);
                        }
                        return null;

                    } else if (item.getClass().isAssignableFrom(ColorItem.class)) {
                        if (!param.equals("rgb") && !param.equals("rgbw") && !param.equals("hsb")) {
                            logger.warn(
                                    "Item %s supported datatypes: hsb, rgb or rgbw. Type %s is ignored. Setted to rgb.",
                                    item.getName(), param);
                            return null;
                        } else {
                            return SimaticBindingConfig.getDataType(param);
                        }

                    } else if (item.getClass().isAssignableFrom(StringItem.class)) {
                        if (!param.startsWith("array")) {
                            logger.warn(
                                    "Item %s support datatype array only. Type %s is ignored. Setted to ARRAY with length 32.",
                                    item.getName(), param);
                        }
                        return null;

                    } else if (item.getClass().isAssignableFrom(ContactItem.class)) {
                        if (!param.equals("byte")) {
                            logger.warn("Item %s support datatype byte only. Type %s is ignored.", item.getName(),
                                    param);
                        }
                        return null;

                    } else if (item.getClass().isAssignableFrom(RollershutterItem.class)) {
                        if (!param.equals("word")) {
                            logger.warn("Item %s support datatype word only. Type %s is ignored.", item.getName(),
                                    param);
                        }
                        return null;

                    } else {
                        throw new BindingConfigParseException("Unsupported item type: " + item);
                    }
                } else {
                    logger.warn("Item %s. Unsupported optional parameter %s", item.getName(), optionalConfigs[i]);
                }
            }
        }

        return null;
    }

    SimaticTypes resolveDataTypeFromItemType(Item item, String address) throws BindingConfigParseException {
        Class<? extends Item> itemType = item.getClass();

        if (itemType.isAssignableFrom(NumberItem.class)) {
            switch (SimaticPLCAddress.create(address).dataType) {
                case DWORD:
                    return SimaticTypes.DWORD;
                case WORD:
                    return SimaticTypes.WORD;
                default:
                    return SimaticTypes.BYTE;
            }
        } else if (itemType.isAssignableFrom(SwitchItem.class)) {
            return SimaticTypes.BYTE;
        } else if (itemType.isAssignableFrom(DimmerItem.class)) {
            return SimaticTypes.BYTE;
        } else if (itemType.isAssignableFrom(ColorItem.class)) {
            logger.warn("Item %s has not specified datatype. Setted to RGB.", item.getName());
            return SimaticTypes.RGB;
        } else if (itemType.isAssignableFrom(StringItem.class)) {
            logger.warn("Item %s has not specified datatype with length. Setted to ARRAY with length 32.",
                    item.getName());
            return SimaticTypes.ARRAY;
        } else if (itemType.isAssignableFrom(ContactItem.class)) {
            return SimaticTypes.BYTE;
        } else if (itemType.isAssignableFrom(RollershutterItem.class)) {
            return SimaticTypes.WORD;
        } else {
            throw new BindingConfigParseException("Unsupported item type: " + item);
        }
    }

    /**
     * This is a helper class holding binding specific configuration details
     *
     * @author VitaTucek
     * @since 1.9.0
     */
    static class SimaticBindingConfig implements BindingConfig {

        public SimaticBindingConfig(Item item, String device, String address, SimaticTypes datatype) {
            this(item, device, address, datatype, 0);
        }

        public SimaticBindingConfig(Item item, String device, String address, SimaticTypes datatype, int direction) {
            super();

            this.item = item;
            this.device = device;
            this.direction = direction;
            this.datatype = datatype;

            this.address = new SimaticPLCAddress(address);
        }

        public SimaticBindingConfig(Item item, String device, String address, SimaticTypes datatype, int direction,
                int dataLength) {
            super();

            this.item = item;
            this.device = device;
            this.direction = direction;
            this.datatype = datatype;

            this.address = new SimaticPLCAddress(address, dataLength);
        }

        // // put member fields here which holds the parsed values
        protected final Item item;
        // Class<? extends Item> itemType;

        protected final int direction;
        protected final String device;
        // protected final String address;
        protected final SimaticTypes datatype;

        SimaticPLCAddress address;

        /**
         * Return item data length
         *
         * @return
         */
        public int getDataLength() {
            return address.getDataLength();
        }

        /**
         * Return item data type
         *
         * @return
         */
        public SimaticTypes getDataType() {
            return datatype;
        }

        /**
         * Return item data type
         *
         * @return
         */
        public static SimaticTypes getDataType(String datatype) {
            if (datatype.equals("byte")) {
                return SimaticTypes.BYTE;
            }
            if (datatype.equals("word")) {
                return SimaticTypes.WORD;
            }
            if (datatype.equals("dword")) {
                return SimaticTypes.DWORD;
            }
            if (datatype.equals("float")) {
                return SimaticTypes.FLOAT;
            }
            if (datatype.equals("hsv")) {
                return SimaticTypes.HSB;
            }
            if (datatype.equals("rgb")) {
                return SimaticTypes.RGB;
            }
            if (datatype.equals("rgbw")) {
                return SimaticTypes.RGBW;
            }

            Matcher matcher = Pattern.compile("^array\\[(\\d+)\\]$").matcher(datatype);
            if (matcher.matches()) {
                return SimaticTypes.ARRAY;
            }

            if (logger.isDebugEnabled()) {
                logger.debug("getDataType() - unresolved type: " + datatype);
            }
            return SimaticTypes.UNKNOWN;
        }

        @Override
        public String toString() {
            return item.getName() + " (Device=" + this.device + " MemAddress=" + this.address + " DataType="
                    + this.getDataType() + " Direction=" + this.direction + ")";
        }

        public SimaticPLCAreaTypes getArea() {
            return address.area;
        }

        public SimaticPLCAddress getAddress() {
            return address;
        }

        public Item getOpenHabItem() {
            return item;
        }

        public String getName() {
            return item.getName();
        }
    }

    /**
     * This is a helper class holding binding info configuration details
     *
     * @author vita
     * @since 1.9.0
     */
    class SimaticInfoBindingConfig implements BindingConfig {

        /**
         *
         */
        public Item item;
        /**
         * Contains device(port) name ex.: plc01
         */
        public String device;
        /**
         * Requested info type
         */
        public InfoType infoType;
    }

    public enum InfoType {
        STATE,
        PREVIOUS_STATE,
        STATE_CHANGE_TIME,
        PACKET_LOST
    }
}
