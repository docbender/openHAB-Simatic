package org.openhab.binding.simatic.internal.simatic;

public class SimaticReadException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = -77359800546962181L;

    public final boolean fatal;

    public SimaticReadException(SimaticReadDataArea area, Exception ex) {
        super(String.format("Read data area error (Area=%s, Error=%s)", area.toString(), ex.getMessage()));

        fatal = true;
    }

    public SimaticReadException(SimaticReadDataArea area, String message, boolean fatal) {
        super(message);

        this.fatal = fatal;
    }
}
