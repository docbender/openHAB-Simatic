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

import java.io.InputStream;
import java.io.OutputStream;

public class NetLinkProInterface extends PLCinterface {

    public NetLinkProInterface(OutputStream os, InputStream is, String name, int localMPI, int protocol) {
        init(os, is, name, localMPI, protocol);
        timeout = 1500;
    }

    /*
     * Send a string of init data to the NetLinkPro adapter.
     */
    int initStep(int nr, byte[] fix, int len, String initRoutine, byte[] buffer) {
        int i;
        if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
            System.out.println(name + " step " + nr + "\n");
        }

        sendWithCRCNLpro(fix, len);
        i = readMPINLpro(buffer);
        return 0;
    }

    int sendWithCRCNLpro(byte[] b, /* a buffer containing the message */
            int size /* the size of the string */
    ) {
        byte[] target = new byte[Nodave.MAX_RAW_LEN];
        // uc target[daveMaxRawLen];
        int i, targetSize = 2;
        target[0] = (byte) (size / 256);
        target[1] = (byte) (size % 256);

        for (i = 0; i < size; i++) {
            target[targetSize] = b[i];
            targetSize++;
        }
        ;
        if ((Nodave.Debug & Nodave.DEBUG_RAWSEND) != 0) {
            Nodave.dump("sendWithCRC", target, 0, targetSize);
        }
        write(target, 0, targetSize);
        return 0;
    }

    int readMPINLpro(byte[] b) {
        int res, length;
        res = read(b, 0, 2);
        // res=read(di->fd.rfd, b, 2);
        if (res < 2) {
            if ((Nodave.Debug & Nodave.DEBUG_RAWSEND) != 0) {
                // LOG2("res %d ",res);
                Nodave.dump("readISOpacket: short packet", b, res, 0);
            }
            return (0); /* short packet */
        }
        length = b[1] + 0x100 * b[0];
        res += read(b, 2, length);
        if ((Nodave.Debug & Nodave.DEBUG_RAWSEND) != 0) {
            // LOG3("readMPINLpro: %d bytes read, %d needed\n",res, length);
            Nodave.dump("readMPINLpro: packet", b, res, 0);
        }
        return (res);
    }

    /*
     * byte readSingle() {
     * byte[] b = { 0 };
     * int res = read(b, 0, 1);
     * if ((Nodave.Debug & Nodave.DEBUG_RAWREAD) != 0)
     * System.out.println("readSingle " + b[0]);
     * return b[0];
     * }
     */
    /*
     * List reachable devices on this NetLinkPro net.
     */
    public int listReachablePartners(byte[] buf) {
        return 0;
    };

    @Override
    public int initAdapter() {
        return 0;
    }

    /*
     * This is an extended memory compare routine. It can handle don't care and stop flags
     * in the sample data. A stop flag lets it return success.
     */
    static int memcmp(int[] a, byte[] b, int len) {
        for (int i = 0; i < len; i++) {
            if ((byte) (a[i] & 0xff) != b[i]) {
                if ((a[i] & 0x100) != 0x100) {
                    System.out.println("a: " + a[i] + " b:" + b[i]);
                    return i + 1;
                }
                if ((a[i] & 0x200) != 0x200) {
                    return 0;
                }
            }
        }
        return 0;
    };

    @Override
    public int disconnectAdapter() {
        return 0;
    }

}
