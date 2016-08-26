/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal;

import java.util.Calendar;
import java.util.Map;

import org.openhab.binding.simatic.internal.SimaticGenericBindingProvider.InfoType;
import org.openhab.binding.simatic.internal.SimaticGenericBindingProvider.SimaticInfoBindingConfig;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;

/**
 * Status of communication
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimaticPortState {

    public enum PortStates {
        UNKNOWN,
        LISTENING,
        CLOSED,
        NOT_EXIST,
        NOT_AVAILABLE,
        NOT_RESPONDING,
        RESPONSE_ERROR
    }

    /** Current device connection state **/
    private PortStates state = PortStates.UNKNOWN;
    /** Previous device connection state **/
    private PortStates prevState = PortStates.UNKNOWN;
    /** Connection state change time **/
    private Calendar changedSince;
    private EventPublisher eventPublisher;
    /** Item name defined for "State" tag in items file configuration **/
    private String itemState = null;
    /** Item name defined for "PreviousState" tag in items file configuration **/
    private String itemPreviousState = null;
    /** Item name defined for "StateChangeTime" tag in items file configuration **/
    private String itemStateChangeTime = null;

    /**
     * Return port status
     *
     * @return
     */
    public PortStates getState() {
        return state;
    }

    /**
     * Return previous status
     *
     * @return
     */
    public PortStates getPreviusState() {
        return prevState;
    }

    /**
     * Return date when last change occurred
     *
     * @return
     */
    public Calendar getChangeDate() {
        return changedSince;
    }

    /**
     * Set port state
     *
     * @param state
     */
    public void setState(PortStates state) {

        // set state only if previous is different
        if (this.state != state) {
            this.prevState = this.state;
            this.state = state;
            this.changedSince = Calendar.getInstance();

            // update event bus
            if (itemState != null) {
                eventPublisher.postUpdate(itemState, new DecimalType(this.state.ordinal()));
            }
            if (itemPreviousState != null) {
                eventPublisher.postUpdate(itemPreviousState, new DecimalType(this.prevState.ordinal()));
            }
            if (itemStateChangeTime != null) {
                eventPublisher.postUpdate(itemStateChangeTime, new DateTimeType(this.changedSince));
            }
        }
    }

    /**
     * Set binding data for internal use and port item state init
     *
     * @param eventPublisher
     * @param itemsInfoConfig
     * @param deviceName
     */
    public void setBindingData(EventPublisher eventPublisher, Map<String, SimaticInfoBindingConfig> itemsInfoConfig,
            String deviceName) {
        this.eventPublisher = eventPublisher;

        for (Map.Entry<String, SimaticInfoBindingConfig> item : itemsInfoConfig.entrySet()) {

            if (item.getValue().device.equals(deviceName)) {
                // find right info type
                if (item.getValue().infoType == InfoType.STATE) {
                    itemState = item.getValue().item.getName();
                } else if (item.getValue().infoType == InfoType.PREVIOUS_STATE) {
                    itemPreviousState = item.getValue().item.getName();
                } else if (item.getValue().infoType == InfoType.STATE_CHANGE_TIME) {
                    itemStateChangeTime = item.getValue().item.getName();
                }
            }
        }
    }
}
