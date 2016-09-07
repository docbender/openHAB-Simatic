/*
 Part of Libnodave, a free communication libray for Siemens S7 300/400 via
 the MPI adapter 6ES7 972-0CA22-0XAC
 or  MPI adapter 6ES7 972-0CA33-0XAC
 or  MPI adapter 6ES7 972-0CA11-0XAC.

 (C) Thomas Hergenhahn (thomas.hergenhahn@web.de) 2002.

 Libnodave is free software; you can redistribute it and/or modify
 it under the terms of the GNU Library General Public License as published by
 the Free Software Foundation; either version 2, or (at your option)
 any later version.

 Libnodave is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU Library General Public License
 along with this; see the file COPYING.  If not, write to
 the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.
*/
package org.openhab.binding.simatic.libnodave;

import java.io.IOException;

public class PPIConnection extends S7Connection {
    public static final byte SYN = 0x16;
    public static final byte DLE = 0x10;
    int PPIAdr;
    public int retries2;
    public int retries3;

    public PPIConnection(PLCinterface ifa, int mpi) {
        super(ifa);
        ifa.timeout = 5500;
        // MPIAdr = mpi;
        PPIAdr = mpi;
        PDUstartIn = 7;
        PDUstartOut = 3;
    }

    Object oo;

    @Override
    public synchronized int exchange(PDU p1) throws IOException {
        int i, res = 0, len, expectedLen = 6, sum;
        boolean expectingLength = true;
        boolean myturn = true;
        msgOut[0] = (byte) PPIAdr; // address ?
        msgOut[1] = (byte) iface.localMPI;
        msgOut[2] = (byte) 108;
        len = 3 + p1.hlen + p1.plen + p1.dlen;
        // The 3 fix bytes + all parts of PDU
        sendLength(len);
        sendIt(msgOut, 0, len);
        i = readCharsPPI(res, 2 * tmo_normal);
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println("i: " + i + " res: " + res);
        }
        if (i == 0) {
            retries2++;
            sendLength(len);
            sendIt(msgOut, 0, len);
            i = readCharsPPI(res, 2 * tmo_normal);
            if (i == 0) {
                retries3++;
                sendLength(len);
                sendIt(msgOut, 0, len);
                i = readCharsPPI(res, 4 * tmo_normal);
                if (i == 0) {
                    if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                        System.out.println("timeout in daveExchangePPI!");
                    }
                    return 512;
                }
            }
        }
        sendRequestData(0);

        return getResponse3();
    }

    public int getResponse3() throws IOException {

        int i, res = 0, len, expectedLen = 6, sum;
        boolean expectingLength = true;
        boolean myturn = true;

        res = 0;
        while ((expectingLength) || (res < expectedLen)) {
            i = readCharsPPI(res, 2 * tmo_normal);
            res += i;
            if (i == 0) {
                return 512;
            } else {
                if (expectingLength && (res == 1) && (msgIn[0] == (byte) 0XE5)) {
                    if (myturn) {
                        sendRequestData(1);
                        res = 0;
                        myturn = false;
                    } else {
                        sendRequestData(0);
                        res = 0;
                        myturn = true;
                    }
                }
                if (expectingLength && (res >= 4) && (msgIn[0] == msgIn[3]) && (msgIn[1] == msgIn[2])) {
                    expectedLen = Nodave.USByte(msgIn, 1) + 6;
                    expectingLength = false;
                    // System.out.println("got length: "+expectedLen);
                }
            }
        }
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println("res " + res + " testing lastChar");
        }
        if (msgIn[res - 1] != SYN) {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("block format error");
            }
            return 1024;
        }
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println("testing check sum");
        }
        sum = 0;
        for (i = 4; i < res - 2; i++) {
            sum += msgIn[i];
        }
        sum = sum & 0xff;
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println("I calc: " + sum + " sent: " + Nodave.USByte(msgIn, res - 2));
        }
        if (Nodave.USByte(msgIn, res - 2) != sum) {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("checksum error\n");
            }
            return 2048;
        }
        return 0;

    }

    public int readByteBlock(int area, int DBnum, int start, int len) throws IOException {
        return readBytes(area, DBnum, start, len, null);
    }

    public void sendLength(int len) throws IOException {
        byte b[] = { 104, 0, 0, 104 };
        b[1] = (byte) len;
        b[2] = (byte) len;
        iface.write(b, 0, 4);
        if ((Nodave.Debug & Nodave.DEBUG_CONN) != 0) {
            Nodave.dump("I send", b, 0, 4);
        }
    }

    public int readCharsPPI(int offset, int tmo) throws IOException {
        int res = 0;
        res = iface.read(msgIn, offset, 512);
        return res;
    }

    @Override
    public void sendRequestData(int alt) throws IOException {
        byte b[] = { DLE, 0, 0, 0x5C, 0, 0 };
        b[1] = (byte) PPIAdr;
        b[2] = (byte) (iface.localMPI);
        if (alt == 0) {
            b[3] = 0x5C;
        } else {
            b[3] = 0x7C;
        }
        iface.write(b, 0, 1);
        sendIt(b, 1, b.length - 3);
    }

    public void sendIt(byte[] b, int start, int len) throws IOException {
        int i;
        int sum = 0;
        for (i = 0; i < len; i++) {
            sum += b[i + start];
        }
        sum = sum & 0xff;
        b[len + start] = (byte) sum;
        len++;
        b[len + start] = SYN;
        len++;
        iface.write(b, start, len);
        if ((Nodave.Debug & Nodave.DEBUG_CONN) != 0) {
            Nodave.dump("I send", b, start, len);
        }
    }

    @Override
    public int getResponse() throws IOException {
        int res, expectedLen, expectingLength, i, sum, alt;
        res = 0;
        expectedLen = 6;
        expectingLength = 1;
        alt = 1;
        while ((expectingLength != 0) || (res < expectedLen)) {
            // i = _daveReadChars(dc->iface, dc->msgIn+res, 2000000, daveMaxRawLen);
            i = readCharsPPI(res, 2 * tmo_normal);

            res += i;
            if ((Nodave.Debug & Nodave.DEBUG_RAWREAD) != 0) {
                System.out.println("i:" + i + " res:" + res);
            }
            if (i == 0) {
                // return 512
                ;
            } else {
                if ((expectingLength != 0) && (res == 1) && (msgIn[0] == 0xE5)) {
                    if (alt != 0) {
                        sendRequestData(alt);
                        res = 0;
                        alt = 0;
                    } else {
                        sendRequestData(alt);
                        res = 0;
                        alt = 1;
                    }
                }
                if ((expectingLength != 0) && (res >= 4) && (msgIn[0] == msgIn[3]) && (msgIn[1] == msgIn[2])) {
                    expectedLen = msgIn[1] + 6;
                    expectingLength = 0;
                }
            }
        }
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println("res " + res + " testing lastChar");
        }
        if (msgIn[res - 1] != SYN) {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("block format error");
            }
            return 1024;
        }
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println("testing check sum");
        }
        sum = 0;
        for (i = 4; i < res - 2; i++) {
            sum += msgIn[i];
        }
        sum = sum & 0xff;
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println("I calc: " + sum + " sent: " + Nodave.USByte(msgIn, res - 2));
        }
        if (Nodave.USByte(msgIn, res - 2) != sum) {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("checksum error\n");
            }
            return 2048;
        }
        return 0;
    }

    /*
     * public int getResponse() {
     * int res, expectedLen, i, sum;
     * res = 0;
     * expectedLen = 6;
     * boolean expectingLength = true;
     * while ((expectingLength) || (res < expectedLen)) {
     * i = readCharsPPI(res, 2 * tmo_normal);
     * res += i;
     * if (i == 0) {
     * return 512;
     * } else {
     * if (expectingLength
     * && (res >= 4)
     * && (msgIn[0] == msgIn[3])
     * && (msgIn[1] == msgIn[2])) {
     * expectedLen = Nodave.USByte(msgIn, 1) + 6;
     * expectingLength = false;
     * // System.out.println("got length: "+expectedLen);
     * }
     * }
     * }
     *
     * if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0)
     * System.out.println("res " + res + " testing lastChar");
     * if (msgIn[res - 1] != SYN) {
     * if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0)
     * System.out.println("block format error");
     * return 1024;
     * }
     * if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0)
     * System.out.println("testing check sum");
     * sum = 0;
     * for (i = 4; i < res - 2; i++) {
     * sum += msgIn[i];
     * }
     * sum = sum & 0xff;
     * if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0)
     * System.out.println(
     * "I calc: " + sum + " sent: " + Nodave.USByte(msgIn, res - 2));
     * if (Nodave.USByte(msgIn, res - 2) != sum) {
     * if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0)
     * System.out.println("checksum error\n");
     * return 2048;
     * }
     * return 0;
     * }
     */

    @Override
    public int sendMsg(PDU p1) throws IOException {
        int len, res = 0, i;
        msgOut[0] = (byte) PPIAdr; // address ?
        msgOut[1] = (byte) iface.localMPI;
        msgOut[2] = (byte) 0x5c;
        len = 3 + p1.hlen + p1.plen + p1.dlen;
        // The 3 fix bytes + all parts of PDU
        sendLength(len);
        sendIt(msgOut, 0, len);
        i = readCharsPPI(res, 12 * tmo_normal);
        System.out.println("result " + i);
        return i;
    }

    @Override
    public int getPPIresponse() throws IOException {
        int res, expectedLen, i, sum;
        res = 0;
        expectedLen = 6;
        boolean expectingLength = true;
        while ((expectingLength) || (res < expectedLen)) {
            i = readCharsPPI(res, 2 * tmo_normal);
            res += i;
            if (i == 0) {
                return 512;
            } else {
                if (expectingLength && (res >= 4) && (msgIn[0] == msgIn[3]) && (msgIn[1] == msgIn[2])) {
                    expectedLen = Nodave.USByte(msgIn, 1) + 6;
                    expectingLength = false;
                    // System.out.println("got length: "+expectedLen);
                }
            }
        }
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println("res " + res + " testing lastChar");
        }
        if (msgIn[res - 1] != SYN) {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("block format error");
            }
            return 1024;
        }
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println("testing check sum");
        }
        sum = 0;
        for (i = 4; i < res - 2; i++) {
            sum += msgIn[i];
        }
        sum = sum & 0xff;
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println("I calc: " + sum + " sent: " + Nodave.USByte(msgIn, res - 2));
        }
        if (Nodave.USByte(msgIn, res - 2) != sum) {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("checksum error\n");
            }
            return 2048;
        }
        return 0;
    }

}
