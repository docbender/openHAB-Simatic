package org.openhab.binding.simatic.internal.simatic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.items.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class SimaticBindingConfig {
    private static final Logger logger = LoggerFactory.getLogger(SimaticBindingConfig.class);

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
        if (datatype.equals("hsb")) {
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
