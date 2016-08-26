package org.openhab.binding.simatic.internal;

import java.util.LinkedList;

public class SimaticReadWriteQueue {
    LinkedList<SimaticReadWriteDataArea> data = new LinkedList<SimaticReadWriteDataArea>();

    public void put(SimaticReadWriteDataArea item) {
        data.offer(item);
    }

    public LinkedList<SimaticReadWriteDataArea> getData() {
        return data;
    }

}
