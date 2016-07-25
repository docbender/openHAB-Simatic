/*
 Part of Libnodave, a free communication libray for Siemens S7 300/400 via
 the MPI adapter 6ES7 972-0CA22-0XAC
 or  MPI adapter 6ES7 972-0CA33-0XAC
 or  MPI adapter 6ES7 972-0CA11-0XAC.

 (C) Thomas Hergenhahn (thomas.hergenhahn@web.de) 2002..2005

 Libnodave is free software; you can redistribute it and/or modify
 it under the terms of the GNU Library General Public License as published by
 the Free Software Foundation; either version 2, or (at your option)
 any later version.

 Libnodave is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Library General Public License for more details.

 You should have received a copy of the GNU Library General Public License
 along with this; see the file COPYING.  If not, write to
 the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.
*/
package org.openhab.binding.simatic.libnodave;

public class IBH_MPIConnection extends S7Connection {
    byte MPI;

    public IBH_MPIConnection(PLCinterface ifa, int mpi) {
        super(ifa);
        this.MPI = (byte) mpi;
        PDUstartIn = 8 + 7;
        PDUstartOut = 8 + 7;
    }

    private int readPacket() {
        int res = iface.read(msgIn, 0, 4);
        if (res == 4) {
            int len = 0x100 * msgIn[2] + msgIn[3];
            res += iface.read(msgIn, 4, len);
        }
        return res;
    }

    int writeIBH(byte[] buffer, int len) {
        iface.write(buffer, 0, len);
        if ((Nodave.Debug & Nodave.DEBUG_IFACE) != 0) {
            Nodave.dump("writeIBH ", buffer, 0, len);
        }
        return 0;
    }

    int readIBHPacket() {
        // System.out.println("readIBHPacket");
        int i, res = 0;
        res = iface.read(msgIn, 0, 3);
        // System.out.println("readIBHPacket res:" + res);
        if (res == 3) {
            int len = Nodave.USByte(msgIn, 2) + 5;
            res += iface.read(msgIn, 3, len);
        } else {
            if ((Nodave.Debug & Nodave.DEBUG_IFACE) != 0) {
                System.out.println("res " + res);
                Nodave.dump("readIBHpacket: short packet", msgIn, 0, res);
            }
            return (0); // short packet
        }
        if ((Nodave.Debug & Nodave.DEBUG_IFACE) != 0) {

            System.out.println("readIBHpacket: " + res + " bytes read, " + (msgIn[2] + 8) + " needed");
            Nodave.dump("readIBHpacket: ", msgIn, 0, res);
        }
        return (res);
    };

    /*
     * This performs initialization steps with sampled byte sequences. If chal is <>NULL
     * it will send this byte sequence.
     * It will then wait for a packet and compare it to the sample.
     */
    int initStepIBH(byte[] chal, int[] resp, int rl) {
        int res = 0, a = 0;
        int res2;
        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("_daveInitStepIBH before write.\n");
        }
        res = writeIBH(chal, chal.length);
        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("_daveInitStepIBH write returned " + res);
        }
        // if (res < 0)
        // return 100;
        res = readIBHPacket();
        /*
         * We may get a network layer ackknowledge and an MPI layer ackknowledge, which we discard.
         * So, normally at least the 3rd packet should have the desired response.
         * Waiting for more does:
         * -discard extra packets resulting from last step.
         * -extend effective timeout.
         */
        while (a < 5) {
            if (a != 0) {
                res = readIBHPacket();
                // _daveDump("got:",b,res);
            }
            if (res > 0) {
                res2 = MPIinterface.memcmp(resp, msgIn, rl / 2);
                if (res2 == 0) {
                    if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
                        System.out.println("*** Got response " + res + " " + rl);
                    }
                    return a;
                } else {
                    if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
                        System.out.println("wrong! " + res2);
                    }
                }
            }
            a++;
        }
        return a;
    }

    byte chal3[] = { 0x07, (byte) 0xff, 0x06, 0x01, 0x00, 0x00, (byte) 0x97, 0x00, 0x15, (byte) 0xff, (byte) 0xf0,
            (byte) 0xf0, (byte) 0xf0, (byte) 0xf0, };

    int resp3[] = { 0xff, 0x07, 0x02, 0x01, 0x97, 0x00, 0x00, 0x00, 0x114, 0x100, };

    byte chal8[] = { 0x07, (byte) 0xff, 0x11, 0x02, 0x00, 0x00, (byte) 0x82, 0x00, 0x14, 0x00, 0x02, 0x01, 0x0c,
            (byte) 0xe0, 0x04, 0x00, (byte) 0x80, 0x00, 0x02, 0x00, 0x02, 0x01, 0x00, 0x01, 0x00, };
    int resp7[] = { 0xff, 0x07, 0x13, 0x00, 0x00, 0x00, 0xc2, 0x02, 0x115, 0x114, 0x02, 0x100, 0x00, 0x22, 0x0c, 0xd0,
            0x04, 0x00, 0x80, 0x00, 0x02, 0x00, 0x02, 0x01, 0x00, 0x01, 0x00, };
    byte chal011[] = { 0x07, (byte) 0xff, 0x07, 0x03, 0x00, 0x00, (byte) 0x82, 0x00, 0x15, 0x14, 0x02, 0x00, 0x02, 0x05,
            0x01, };

    int resp09[] = { 0xff, 0x07, 0x09, 0x00, 0x00, 0x00, 0xc2, 0x02, 0x115, 0x114, 0x02, 0x100, 0x00, 0x22, 0x02, 0x05,
            0x01, };

    // byte[] ibhPacketHeader = new byte[8];
    // number of bytes counted from the ninth one.
    // a counter, response packets refer to request packets
    // final static int ibhp_sFlags = 4; // my guess
    // final static int ibhp_rFlags = 6; // my interpretation

    byte src_conn;
    byte dst_conn;

    @Override
    public int connectPLC() {
        int a = 0, res, retries;
        PDU p1;
        src_conn = 20 - 1;
        dst_conn = 20 - 1;
        chal8[10] = MPI;
        retries = 0;
        do {
            // System.out.println("trying next ID:\n");
            src_conn++;
            chal3[8] = src_conn;
            a = initStepIBH(chal3, resp3, resp3.length);
            retries++;

        } while ((msgIn[9] != 0) && (retries < 10));
        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("_daveInitStepIBH 4: " + a);
        }
        if (a > 3) {
            return -4;
        }
        ;

        chal8[8] = src_conn;
        a = initStepIBH(chal8, resp7, resp7.length);
        dst_conn = msgIn[9];
        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("daveInitStepIBH 5:" + a + " connID: " + dst_conn);
        }
        if (a > 3) {
            return -5;
        }

        chal011[8] = src_conn;
        chal011[9] = dst_conn;
        chal011[10] = MPI;
        a = initStepIBH(chal011, resp09, resp09.length);
        dst_conn = msgIn[9];
        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("_daveInitStepIBH 5a: " + a + " connID: " + dst_conn);
        }
        if (a > 3) {
            return -5;
        }

        packetNumber = 0;
        messageNumber = 0;
        return negPDUlengthRequest();
        // return 0;
    }

    int ibhPacketNumber = 0;

    int packPDU(PDU p) {
        msgOut[0] = 7;
        msgOut[1] = (byte) 0xff;

        msgOut[2] = (byte) (5 + 2 + p.hlen + p.plen + p.dlen);
        msgOut[3] = (byte) packetNumber;
        msgOut[6] = (byte) 0x82;
        msgOut[7] = 0;
        msgOut[8] = src_conn;
        msgOut[9] = dst_conn;
        msgOut[10] = MPI;
        msgOut[11] = (byte) 0;
        msgOut[12] = (byte) (2 + p.hlen + p.plen + p.dlen);
        msgOut[10] = MPI;
        // msgOut[12] = (byte) 0;
        // msgOut[14] = (byte) (packetNumber-4);
        msgOut[14] = (byte) (ibhPacketNumber);
        msgOut[13] = (byte) 0xF1;
        if (msgOut[p.header + 4] == 0) {
            msgOut[p.header + 4] = (byte) (1 + packetNumber);
        }
        // give the PDU a number
        packetNumber++;
        ibhPacketNumber++;
        if (packetNumber == 0) {
            packetNumber = 1;
        }
        return 0;
    }

    byte[] MPIack = { 0x07, (byte) 0xff, 0x08, 0x05, 0x00, 0x00, (byte) 0x82, 0x00, 0x15, 0x14, 0x02, 0x00, 0x03,
            (byte) 0xb0, 0x01, 0x00, };

    void sendMPIAck_IBH() {
        MPIack[15] = msgIn[16];
        MPIack[8] = src_conn;
        MPIack[9] = dst_conn;
        MPIack[10] = MPI;
        writeIBH(MPIack, MPIack.length);
    }

    /*
     * send a network level ackknowledge
     */

    byte[] ack = new byte[13];

    void sendIBHNetAck() {
        System.arraycopy(msgIn, 0, ack, 0, ack.length);
        ack[11] = 1;
        ack[12] = 9;
        writeIBH(ack, ack.length);
    }

    void dumpMPIheader() {
        System.out.println("srcconn: " + msgIn[8 + 0]);
        System.out.println("dstconn: " + msgIn[8 + 1]);
        System.out.println("MPI:        " + msgIn[8 + 2]);
        System.out.println("MPI len:   " + msgIn[8 + 4]);
        System.out.println("MPI func: " + msgIn[8 + 5]);
    }

    void dumpNetHeader() {
        System.out.println("Channel: " + msgIn[0]);
        System.out.println("Channel: " + msgIn[1]);
        System.out.println("Length:   " + msgIn[2]);
        System.out.println("Number:  " + msgIn[3]);
        Nodave.dump("sFlags: ", msgIn, 4, 2);
        Nodave.dump("rFlags: ", msgIn, 6, 2);
    }

    int needAckNumber;

    final int PACKET_TYPE_MPI_ACK = 10;

    /*
     * packet analysis. mixes all levels.
     */
    int analyze() {
        // System.out.println("enter analyze");
        int haveResp = 0;
        PDU p1;
        needAckNumber = -1; // Assume no ack
        if ((Nodave.Debug & Nodave.DEBUG_RAWREAD) != 0) {
            dumpNetHeader();
        }
        if (msgIn[6] == (byte) 0x82) {
            // pm= (MPIheader*) (dc->msgIn+sizeof(IBHpacket));
            if ((Nodave.Debug & Nodave.DEBUG_RAWREAD) != 0) {
                dumpMPIheader();
            }
            if (msgIn[8 + 5] == (byte) 0xf1) {
                if ((Nodave.Debug & Nodave.DEBUG_RAWREAD) != 0) {
                    System.out.println("MPI packet number: " + msgIn[3] + " needs ackknowledge\n");
                }
                needAckNumber = msgIn[3];
                p1 = new PDU(msgIn, PDUstartIn);
                p1.setupReceivedPDU();
            }
            if (msgIn[8 + 5] == (byte) 0xb0) {
                System.out.println("Ackknowledge for packet number: " + msgIn[3]);
                return PACKET_TYPE_MPI_ACK;
            }
            if (msgIn[8 + 5] == (byte) 0xe0) {
                /*
                 * System.out.println("Connect to MPI: ",pm->MPI);
                 * memcpy(resp, _MPIconnectResponse, sizeof(_MPIconnectResponse));
                 * resp[8]=pm->src_conn;
                 * resp[9]=pm->src_conn;
                 * resp[10]=pm->MPI;
                 * haveResp=1;
                 */
            }
        }

        if ((msgIn[6] == (byte) 0xc2) && (msgIn[7] == 0x2)) {
            // System.out.println("found c202 "+msgIn[8 + 5 + 2]);
            /*
             * MPIheader2 * pm= (MPIheader2*) (dc->msgIn+sizeof(IBHpacket));
             */
            if ((Nodave.Debug & Nodave.DEBUG_RAWREAD) != 0) {
                dumpMPIheader();
            }

            if (msgIn[8 + 5 + 2] == (byte) 0xf1) {
                if ((Nodave.Debug & Nodave.DEBUG_RAWREAD) != 0) {
                    System.out.println("analyze found PDU transport");
                }
                needAckNumber = msgIn[3 + 2]; // ????
                PDUstartIn = 7 + 8 + 2;
                /* sizeof(IBHpacket) + sizeof(MPIheader2); */
                sendMPIAck_IBH();

                return 55;
            }

            if (msgIn[8 + 5 + 2] == (byte) 0xb0) {
                // System.out.println("found MPI ackknowledge");
                if ((Nodave.Debug & Nodave.DEBUG_RAWREAD) != 0) {

                    System.out.println("Ackknowledge for packet number: " + msgIn[3 + 2]);
                }
                // return _davePtMPIAck;
            } else {
                System.out.println("Unsupported MPI function code !!: %d\n" + msgIn[8 + 5 + 2]);
                sendMPIAck_IBH();
            }
        }

        if ((msgIn[6] == (byte) 0x82) || (msgIn[4] == (byte) 0x82)) {
            /*
             * if ((p - > rFlags == 0x82) && (p - > packetNumber) && (p - > len))
             */
            sendIBHNetAck();
        }
        /*
         * if (haveResp) {
         * _daveWriteIBH(dc - > iface, resp, resp[2] + 8);
         * _daveDump("I send response:", resp, resp[2] + 8);
         * }
         */
        return 0;
    };

    @Override
    public int exchange(PDU p) {
        int res, count, pt;
        // System.out.println("enter ExchangeIBH\n");
        packPDU(p);
        res = writeIBH(msgOut, msgOut[2] + 8);
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            Nodave.dump("I send request: ", msgOut, 0, msgOut[2] + 8);
        }
        count = 0;
        do {
            res = readIBHPacket();
            // Nodave.dump("I got: ", msgIn, 0, msgIn[2] + 8);
            count++;
            pt = analyze();
            if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
                System.out.println("ExchangeIBH packet type:" + pt);
            }
        } while ((pt != 55) && (count < 5));
        if (count <= 4) {
            return 0;
        } else {
            return -1;
        }
    }

    byte[] chal31 = { 0x07, (byte) 0xff, 0x06, 0x08, 0x00, 0x00, (byte) 0x82, 0x00, 0x14, 0x14, 0x02, 0x00, 0x01,
            (byte) 0x80, };

    @Override
    public int disconnectPLC() {
        System.out.println("disconnectPLC");
        Nodave.Debug = Nodave.DEBUG_ALL;
        chal31[8] = src_conn;
        chal31[9] = dst_conn;
        chal31[10] = MPI;
        writeIBH(chal31, chal31.length);
        readIBHPacket();
        readIBHPacket();
        Nodave.Debug = Nodave.DEBUG_ALL & ~Nodave.DEBUG_IFACE;
        return 0;
    }

}
