/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.simatic.SimaticBindingProvider;
import org.openhab.binding.simatic.internal.SimaticGenericBindingProvider.SimaticBindingConfig;
import org.openhab.binding.simatic.internal.SimaticGenericBindingProvider.SimaticInfoBindingConfig;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implement this class if you are going create an actively polling service
 * like querying a Website/Device.
 *
 * @author VitaTucek
 * @since 1.9.0
 */
public class SimaticBinding extends AbstractActiveBinding<SimaticBindingProvider> {

    private static final Logger logger = LoggerFactory.getLogger(SimaticBinding.class);

    /**
     * The BundleContext. This is only valid when the bundle is ACTIVE. It is set in the activate()
     * method and must not be accessed anymore once the deactivate() method was called or before activate()
     * was called.
     */
    @SuppressWarnings("unused")
    private BundleContext bundleContext;

    /**
     * the refresh interval which is used to poll values from the Simatic PLC (optional, defaults to 1000ms)
     */
    private long refreshInterval = 1000;

    // devices
    private Map<String, SimaticGenericDevice> devices = new HashMap<String, SimaticGenericDevice>();
    // data item configs
    private Map<String, SimaticBindingConfig> items = new HashMap<String, SimaticBindingConfig>();
    // info item configs
    private Map<String, SimaticInfoBindingConfig> infoItems = new HashMap<String, SimaticInfoBindingConfig>();

    public SimaticBinding() {
    }

    /**
     * Called by the SCR to activate the component with its configuration read from CAS
     *
     * @param bundleContext BundleContext of the Bundle that defines this component
     * @param configuration Configuration properties for this component obtained from the ConfigAdmin service
     */
    public void activate(final BundleContext bundleContext, final Map<String, Object> configuration) {
        this.bundleContext = bundleContext;

        logger.debug("activate() method is called!");
        logger.debug(bundleContext.getBundle().toString());

        // the configuration is guaranteed not to be null, because the component definition has the
        // configuration-policy set to require. If set to 'optional' then the configuration may be null
        // Actually it WAS set to 'optional'. Changed it to 'require' -- AchilleGR

        // to override the default refresh interval one has to add a
        // parameter to openhab.cfg like <bindingName>:refresh=<intervalInMs>
        String refreshIntervalString = (String) configuration.get("refresh");
        if (StringUtils.isNotBlank(refreshIntervalString)) {
            refreshInterval = Long.parseLong(refreshIntervalString);
        }

        // if devices collection isn't empty
        if (devices.size() > 0) {
            logger.debug("Device count {}", devices.size());

            // close all connections
            for (Map.Entry<String, SimaticGenericDevice> item : devices.entrySet()) {
                item.getValue().close();
                item.getValue().unsetBindingData();
            }
        }

        devices.clear();

        Pattern rgxPLCKey = Pattern.compile("^plc\\d*$");
        Pattern rgxPLCValue = Pattern
                .compile("^((\\d{1,3}[.]\\d{1,3}[.]\\d{1,3}[.]\\d{1,3})[:]([0-2])[.](\\d{1,2})(:(OP|PG|S7))?)$");

        for (Map.Entry<String, Object> item : configuration.entrySet()) {
            // port
            if (rgxPLCKey.matcher(item.getKey()).matches()) {
                String plcString = (String) item.getValue();
                if (StringUtils.isNotBlank(plcString)) {

                    Matcher matcher = rgxPLCValue.matcher(plcString);

                    if (!matcher.matches()) {
                        logger.error("{}: Wrong PLC configuration: {}", item.getKey(), plcString);
                        logger.info("PLC configuration example: plc=192.168.1.5:0.15 or plc1=192.168.1.5:0.1:OP");
                        logger.debug("setProperlyConfigured: false");
                        setProperlyConfigured(false);
                        return;
                    }

                    if (matcher.group(6) == null) {
                        devices.put(item.getKey(), new SimaticTCP(item.getKey(), matcher.group(2),
                                Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4))));
                    } else {
                        devices.put(item.getKey(),
                                new SimaticTCP(item.getKey(), matcher.group(2), Integer.parseInt(matcher.group(3)),
                                        Integer.parseInt(matcher.group(4)), matcher.group(6)));
                    }

                } else {
                    logger.error("Blank port configuration");
                    logger.debug("setProperlyConfigured: false");
                    setProperlyConfigured(false);
                    return;
                }
            }
        }
        logger.debug("items: {}", items);
        logger.debug("infoItems: {}", infoItems);

        for (Map.Entry<String, SimaticGenericDevice> item : devices.entrySet()) {
            item.getValue().setBindingData(eventPublisher, items, infoItems);
            item.getValue().prepareData();
            item.getValue().open();
        }

        logger.debug("setProperlyConfigured: true");
        setProperlyConfigured(true);
    }

    /**
     * Called by the SCR when the configuration of a binding has been changed through the ConfigAdmin service.
     *
     * @param configuration Updated configuration properties
     */
    public void modified(final Map<String, Object> configuration) {
        // update the internal configuration accordingly
        logger.debug("modified() method is called!");
        // Reactivating on modified config -- AchilleGR
        activate(this.bundleContext, configuration);

    }

    /**
     * Called by the SCR to deactivate the component when either the configuration is removed or
     * mandatory references are no longer satisfied or the component has simply been stopped.
     *
     * @param reason Reason code for the deactivation:<br>
     *            <ul>
     *            <li>0 – Unspecified
     *            <li>1 – The component was disabled
     *            <li>2 – A reference became unsatisfied
     *            <li>3 – A configuration was changed
     *            <li>4 – A configuration was deleted
     *            <li>5 – The component was disposed
     *            <li>6 – The bundle was stopped
     *            </ul>
     */
    public void deactivate(final int reason) {
        this.bundleContext = null;
        // deallocate resources here that are no longer needed and
        // should be reset when activating this binding again

        logger.debug("deactivate() method is called!");

        for (Map.Entry<String, SimaticGenericDevice> item : devices.entrySet()) {
            item.getValue().unsetBindingData();
            item.getValue().close();
        }

        devices.clear();
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected long getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected String getName() {
        return "Simatic communication service";
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected void execute() {
        // the frequently executed code (polling) goes here ...
        if (logger.isDebugEnabled()) {
            logger.debug("execute() method is called!");
        }

        if (devices != null && devices.size() > 0) {
            // go through all devices
            for (Map.Entry<String, SimaticGenericDevice> item : devices.entrySet()) {
                // should reconnect
                if (!item.getValue().isConnected() || item.getValue().shouldReconnect()) {
                    item.getValue().reconnectWithDelaying();
                }
                if (item.getValue().isConnected()) {
                    // check device for new data
                    item.getValue().checkNewData();
                }
            }
        }
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected void internalReceiveCommand(String itemName, Command command) {
        // the code being executed when a command was sent on the openHAB
        // event bus goes here. This method is only called if one of the
        // BindingProviders provide a binding for the given 'itemName'.
        if (logger.isDebugEnabled()) {
            logger.debug("internalReceiveCommand({},{}) is called!", itemName, command);
        }

        SimaticBindingConfig config = items.get(itemName);

        if (config != null) {
            SimaticGenericDevice device = devices.get(config.device);

            if (device != null) {
                try {
                    device.sendData(itemName, command, config);
                } catch (Exception ex) {
                    logger.error("internalReceiveCommand(): line:" + ex.getStackTrace()[0].getLineNumber() + "|method:"
                            + ex.getStackTrace()[0].getMethodName());
                }
            } else {
                logger.warn("No device for item: {}", itemName);
            }
        } else {
            logger.warn("No config for item: {}", itemName);
        }
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected void internalReceiveUpdate(String itemName, State newState) {
        // the code being executed when a state was sent on the openHAB
        // event bus goes here. This method is only called if one of the
        // BindingProviders provide a binding for the given 'itemName'.
        if (logger.isDebugEnabled()) {
            logger.debug("internalReceiveUpdate({},{}) is called!", itemName, newState);
        }
    }

    @Override
    public void bindingChanged(BindingProvider provider, String itemName) {
        if (logger.isDebugEnabled()) {
            logger.debug("bindingChanged() - itemName:{}", itemName);
        }

        BindingConfig config = ((SimaticGenericBindingProvider) provider).getItemConfig(itemName);

        logger.trace("config: {}", config);
        if (config instanceof SimaticBindingConfig) {

            if (items.get(itemName) != null) {
                items.remove(itemName);
            }

            if (config != null) {
                items.put(itemName, (SimaticBindingConfig) config);
            }

        } else if (config instanceof SimaticInfoBindingConfig) {
            if (infoItems.get(itemName) != null) {
                infoItems.remove(itemName);
            }

            if (config != null) {
                infoItems.put(itemName, (SimaticInfoBindingConfig) config);
            }
        } else if (config == null) {
            // OpenHAB2 (at least) calls bindingChanged twice, the first time it removes the device (null config), then
            // reinserts with new values -- AchilleGR
            items.remove(itemName);
            infoItems.remove(itemName);
        }

        logger.trace("ItemsConfig: {}:{}", items, items.entrySet().size());
        logger.trace("InfoItemsConfig: {}:{}", infoItems, infoItems.entrySet().size());
        logger.trace("Devices: {}", devices);
        for (Map.Entry<String, SimaticGenericDevice> item : devices.entrySet()) {
            item.getValue().unsetBindingData();
            item.getValue().setBindingData(eventPublisher, items, infoItems);
            item.getValue().prepareData();
        }
        // Calling super in the end allows us to stop the polling service if there are no bindings
        super.bindingChanged(provider, itemName);
    }

    @Override
    public void allBindingsChanged(BindingProvider provider) {
        logger.debug("allBindingsChanged({}) is called!", provider);

        items.clear();
        infoItems.clear();

        for (Entry<String, BindingConfig> item : ((SimaticGenericBindingProvider) provider).configs().entrySet()) {
            if (item.getValue() instanceof SimaticBindingConfig) {
                items.put(item.getKey(), (SimaticBindingConfig) item.getValue());
            } else if (item.getValue() instanceof SimaticInfoBindingConfig) {
                infoItems.put(item.getKey(), (SimaticInfoBindingConfig) item.getValue());
            }
        }
        logger.debug("ItemsConfig: {}:{}", items, items.entrySet().size());
        logger.debug("InfoItemsConfig: {}:{}", infoItems, infoItems.entrySet().size());
        logger.debug("Devices: {}", devices);
        for (Map.Entry<String, SimaticGenericDevice> item : devices.entrySet()) {
            item.getValue().unsetBindingData();
            item.getValue().setBindingData(eventPublisher, items, infoItems);
            item.getValue().prepareData();
        }
        // Calling super in the end allows us to stop the polling service if there are no bindings
        super.allBindingsChanged(provider);
    }

}
