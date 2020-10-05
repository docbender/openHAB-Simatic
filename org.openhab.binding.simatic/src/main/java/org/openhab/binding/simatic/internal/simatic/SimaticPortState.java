/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal.simatic;

import java.util.Calendar;

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
    public PortStates getPreviousState() {
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
        }
    }
}
