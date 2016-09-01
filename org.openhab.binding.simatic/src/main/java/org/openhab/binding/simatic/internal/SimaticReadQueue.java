/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simatic.internal;

import java.util.LinkedList;

/**
 *
 * Class holding read areas
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimaticReadQueue {
    LinkedList<SimaticReadDataArea> data = new LinkedList<SimaticReadDataArea>();

    public void put(SimaticReadDataArea item) {
        data.offer(item);
    }

    public LinkedList<SimaticReadDataArea> getData() {
        return data;
    }

}
