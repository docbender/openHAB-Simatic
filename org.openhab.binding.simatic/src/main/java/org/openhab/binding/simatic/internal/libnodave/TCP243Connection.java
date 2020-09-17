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

public class TCP243Connection extends TCPConnection {

    public TCP243Connection(PLCinterface ifa, int rack, int slot) {
        super(ifa, 0, 0);
    }

    @Override
    public int connectPLC() throws IOException {
        int res;
        byte[] b4CP243 = { (byte) 0x11, (byte) 0xE0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00,
                (byte) 0xC1, (byte) 0x02, (byte) 0x4D, (byte) 0x57, (byte) 0xC2, (byte) 0x02, (byte) 0x4D, (byte) 0x57,
                (byte) 0xC0, (byte) 0x01, (byte) 0x09 };

        System.out.println("daveConnectPLC() step 0.");
        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("daveConnectPLC() step 1.");
        }
        System.arraycopy(b4CP243, 0, msgOut, 4, b4CP243.length);
        sendISOPacket(b4CP243.length);
        readISOPacket();
        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("daveConnectPLC() step 1.");
        }
        return negPDUlengthRequest();
    }
}
