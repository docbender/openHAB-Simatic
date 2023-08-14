package org.openhab.binding.simatic.internal.simatic;

import java.io.IOException;

import org.openhab.binding.simatic.internal.libnodave.Nodave;
import org.openhab.binding.simatic.internal.libnodave.S7Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieve and hold PLC information
 *
 * @author VitaTucek
 *
 */
public class SimaticDeviceInfo {
    private static final Logger logger = LoggerFactory.getLogger(SimaticDeviceInfo.class);

    private String moduleTypeName;
    private String serialNumber;
    private String plcName;
    private String copyright;
    private String moduleName;
    private String orderNr;
    private String hwOrderNr;
    private String hwVersion;
    private String fwVersion;
    private long memorySize;

    /**
     * Get readable result
     *
     * @param source
     * @return
     */
    private String getString(String source) {
        /*
         * if (source == null) {
         * return "-";
         * }
         */
        return source;
    }

    public String getModuleTypeName() {
        return getString(moduleTypeName);
    }

    public String getSerialNumber() {
        return getString(serialNumber);
    }

    public String getPlcName() {
        return getString(plcName);
    }

    public String getCopyright() {
        return getString(copyright);
    }

    public String getModuleName() {
        return getString(moduleName);
    }

    public String getOrderNr() {
        return getString(orderNr);
    }

    public String getHwOrderNr() {
        return getString(hwOrderNr);
    }

    public String getHwVersion() {
        return getString(hwVersion);
    }

    public String getFwVersion() {
        return getString(fwVersion);
    }

    public String getMemorySize() {
        if (memorySize == -1) {
            return null;
        } else if (memorySize > 1024 * 1024) {
            return String.valueOf(memorySize / (1024 * 1024)) + " MB";
        } else if (memorySize > 1024) {
            return String.valueOf(memorySize / 1024) + " kB";
        } else {
            return String.valueOf(memorySize) + " B";
        }
    }

    /**
     * Get connected device information
     *
     * @param dc
     * @throws IOException
     */
    public void getInfo(S7Connection dc) throws IOException {
        S7Connection.S7CpuInfo cpuinfo = dc.new S7CpuInfo();
        int res;
        if ((res = dc.GetCpuInfo(cpuinfo)) == 0) {
            moduleName = cpuinfo.ModuleName;
            moduleTypeName = cpuinfo.ModuleTypeName;
            plcName = cpuinfo.PlcName;
            serialNumber = cpuinfo.SerialNumber;
            copyright = cpuinfo.Copyright;
        } else {
            logger.debug("{} - GetCpuInfo error: {}", this.toString(), Nodave.strerror(res));
        }
        S7Connection.S7ModuleInfo moduleInfo = dc.new S7ModuleInfo();
        if ((res = dc.GetModuleInfo(moduleInfo)) == 0) {
            orderNr = moduleInfo.OrderNr;
            hwOrderNr = moduleInfo.HwOrderNr;
            hwVersion = moduleInfo.HwVersion;
            fwVersion = moduleInfo.FwVersion;
        } else {
            logger.debug("{} - GetModuleInfo error: {}", this.toString(), Nodave.strerror(res));
        }
        S7Connection.S7MemoryInfo memoryInfo = dc.new S7MemoryInfo();
        if ((res = dc.GetMemoryInfo(memoryInfo)) == 0) {
            memorySize = memoryInfo.Size;
        } else {
            memorySize = -1;
            logger.debug("{} - GetMemoryInfo error: {}", this.toString(), Nodave.strerror(res));
        }
    }
}
