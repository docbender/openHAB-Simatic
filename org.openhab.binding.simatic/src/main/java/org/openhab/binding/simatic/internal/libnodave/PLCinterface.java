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
package org.openhab.binding.simatic.internal.libnodave;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PLCinterface {
    int localMPI; // the adapter's MPI address
    String name;
    int timeout = 1000; // Timeout in milliseconds used in transort.
    int protocol; // The kind of transport used on this interface.

    OutputStream out;
    InputStream in;
    int wp, rp;

    public PLCinterface(OutputStream out, InputStream in, String name, int localMPI, int protocol) {
        init(out, in, name, localMPI, protocol);
    }

    public PLCinterface() {
    }

    public void init(OutputStream oStream, InputStream iStream, String name, int localMPI, int protocol) {
        this.out = oStream;
        this.in = iStream;
        this.name = name;
        this.localMPI = localMPI;
        this.protocol = protocol;
    }

    public void write(byte[] b, int start, int len) throws IOException {
        if ((Nodave.Debug & Nodave.DEBUG_IFACE) != 0) {
            Nodave.dump("Interface.write", b, start, len);
        }
        try {
            out.write(b, start, len);
            out.flush();
        } catch (IOException e) {
            System.err.println("Interface.write: " + e);
            throw e;
        }
    }

    public int read(byte[] b, int start, int len) throws IOException {
        int res;
        if ((Nodave.Debug & Nodave.DEBUG_IFACE) != 0) {
            System.out.println("Interface.read");
        }
        try {
            int retry = 0;
            while ((in.available() <= 0) && (retry < timeout / 5)) {
                try {
                    if (retry > 0) {
                        Thread.sleep(5);
                    }
                    retry++;
                    if ((Nodave.Debug & Nodave.DEBUG_IFACE) != 0) {
                        System.out.println("Interface.read delayed");
                    }
                } catch (InterruptedException e) {
                    System.out.println(e);
                }
            }
            res = 0;
            int r, s = start, l = len;
            while ((in.available() > 0) && (l > 0)) {
                if ((Nodave.Debug & Nodave.DEBUG_IFACE) != 0) {
                    System.out.println("can read - in.available()=" + in.available());
                }
                r = in.read(b, s, l);
                s += r;
                l -= r;
                res += r;
                if ((Nodave.Debug & Nodave.DEBUG_IFACE) != 0) {
                    System.out.println("got " + res + "bytes");
                }
            }

            return res;
            // return 0;
        } catch (IOException e) {
            System.out.println(e);
            throw e;
            // return 0;
        }
    }

    public int initAdapter() throws IOException {
        return 0;
    }

    public int disconnectAdapter() throws IOException {
        return 0;
    }

    @Override
    public void finalize() throws Throwable {
        // System.out.println("this is finalize");
        disconnectAdapter();
    }
}
