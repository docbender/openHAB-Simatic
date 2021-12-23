/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.simatic.internal.simatic;

/**
 *
 * Value Update Mode enumeration
 *
 * @author Vita Tucek
 * @since 3.2.0
 */
public enum SimaticUpdateMode {
    OnChange,
    Poll;

    public static boolean validate(String mode) {
        return mode.equals("OnChange") || mode.equals("Poll");
    }

    public static SimaticUpdateMode fromString(String mode) {
        if (!validate(mode)) {
            return OnChange;
        }
        return SimaticUpdateMode.valueOf(mode);
    }
}
