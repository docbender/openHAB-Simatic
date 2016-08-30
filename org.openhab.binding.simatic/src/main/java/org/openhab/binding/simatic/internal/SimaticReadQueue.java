package org.openhab.binding.simatic.internal;

import java.util.LinkedList;

public class SimaticReadQueue {
    LinkedList<SimaticReadDataArea> data = new LinkedList<SimaticReadDataArea>();

    public void put(SimaticReadDataArea item) {
        data.offer(item);
    }

    public LinkedList<SimaticReadDataArea> getData() {
        return data;
    }

}
