/*
 Part of Libnodave, a free communication libray for Siemens S7

 (C) Thomas Hergenhahn (thomas.hergenhahn@web.de) 2005.

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

public class MPIConnection extends S7Connection {
    byte needAckNumber;
    MPIinterface mpiface;
    int MPI;
    int ackPos;
    public static final byte STX = 0x2;
    public static final byte ETX = 0x3;
    public static final byte SYN = 0x16;
    public static final byte DLE = 0x10;

    public MPIConnection(PLCinterface ifa, int mpi) {
        super(ifa);
        MPI = mpi;
        mpiface = (MPIinterface) iface;
        PDUstartIn = 8;
        PDUstartOut = 8;
        ackPos = 6;
    }

    int sendDialog2(int size) throws IOException {
        mpiface.sendSingle(STX);
        if (mpiface.readSingle() != DLE) {
            System.out.println("*** no DLE before send.");
            return -1;
        }
        if (size > 5) {
            msgOut[ackPos + 1] = messageNumber;
            if (msgOut[ackPos + 6] == 0) {
                /* do not number already numbered PDUs 12/10/04 */
                msgOut[ackPos + 6] = (byte) ((messageNumber + 1) & 0xff);
            }
            needAckNumber = messageNumber;
            messageNumber++;
            if (messageNumber == 0) {
                messageNumber = 1;
            }
            messageNumber &= 0xff; // !!

        }
        sendWithPrefix2(size);
        if (mpiface.readSingle() != DLE) {
            System.out.println("*** no DLE after send.");
            return -2;
        }
        return 0;
    }

    @Override
    public int exchange(PDU p1) throws IOException {
        int res;
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println(" enter MPI.Exchange");
        }
        if (sendDialog2(2 + p1.hlen + p1.plen + p1.dlen) != 0) {
            System.out.println("*** Exchange error in sendDialog.");
            return -1;
        }
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println("Exchange send done. needAck " + needAckNumber);
        }
        if (mpiface.readSingle() != STX) {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("*** Exchange no STX after sendDialog(1).");
            }
            if (mpiface.readSingle() != STX) {
                if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                    System.out.println("*** Exchange no STX after sendDialog(2).");
                }
                return -2;
            }
        }
        mpiface.sendSingle(DLE);
        getAck(needAckNumber);
        mpiface.sendSingle(DLE);
        answLen = 0;
        res = mpiface.readSingle();
        if (res == 0) {
            res = mpiface.readSingle();
        }

        if (res == STX) {
            mpiface.sendSingle(DLE);
            if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
                System.out.println("Exchange receive message.");
            }
            res = readMPI4();
            if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
                System.out.println("Exchange got " + answLen + " bytes\n");
            }
            if (mpiface.readSingle() != DLE) {
                if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                    System.out.println("Exchange no DLE.");
                }
            }
            sendAck(msgIn[ackPos + 1]);
            if (mpiface.readSingle() != DLE) {
                if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                    System.out.println("*** Exchange no DLE after Ack.");
                }
            }
        } else {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("*** Exchange no STX after Ack.");
            }
            return -3;
        }
        return 0;
    }

    void sendAck(byte ackNr) throws IOException {
        byte[] ack = new byte[3];
        if ((Nodave.Debug & Nodave.DEBUG_CONN) != 0) {
            System.out.println("sendAck for message " + ackNr);
        }
        ack[0] = (byte) 0xB0;
        ack[1] = 0x01;
        ack[2] = ackNr;
        sendWithPrefix(ack, ack.length);
    }

    int getAck(int nr) throws IOException {
        byte[] b1 = new byte[Nodave.MAX_RAW_LEN];
        int res = mpiface.readMPI(b1);
        if (res < 0) {
            return res - 10;
        }
        if ((res != ackPos + 6) /* && ((res!=13) && (nr==DLE)) */
        ) { // fixed by Andrew Rostovtsew
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("*** getAck(): wrong length " + res + " for ack. Waiting for " + nr + "\n dump:");
                Nodave.dump("wrong ack:", b1, 0, res);
            }
            return -1;
        }
        if (b1[ackPos] != (byte) 0xB0) {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("*** getAck char[" + ackPos + "]= " + b1[ackPos] + " no ack");
            }
            return -2;
        }
        if (b1[ackPos + 2] != nr) {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("*** getAck got: " + b1[ackPos + 2] + " need: " + nr);
            }
            return -3;
        }
        return 0;
    }

    int readMPI4() throws IOException {
        return mpiface.readMPI2(msgIn);
    }

    int readMPI1(byte[] b) throws IOException {
        int res = mpiface.readMPI(b);
        if (res > 1) {
            mpiface.sendSingle(DLE);
        }
        return res;
    }

    /*
     * This adds a prefix to a string and theen sends it
     * after doubling DLEs in the String
     * and adding DLE,ETX and bcc.
     */
    int sendWithPrefix(byte[] b, int size) throws IOException {
        byte[] target = new byte[Nodave.MAX_RAW_LEN];
        byte[] fix = { 04, (byte) 0x80, (byte) 0x80, 0x0C, 0x03, 0x14 };
        if ((Nodave.Debug & Nodave.DEBUG_RAWSEND) != 0) {
            System.out.println("enter sendWithPrefix(), " + size + " chars.");
        }
        target[0] = fix[0];
        target[1] = fix[1];
        target[2] = fix[2];
        target[3] = fix[3];
        // fix[4]=dc->connectionNumber2; // 1/10/05 trying Andrew's patch
        // fix[5]=dc->connectionNumber; // 1/10/05 trying Andrew's patch
        target[4] = fix[4];
        target[5] = fix[5];
        target[1] |= MPI;
        System.arraycopy(b, 0, target, fix.length, size);
        return mpiface.sendWithCRC(target, size + fix.length);
    }

    int sendWithPrefix2(int size) throws IOException {
        byte fix[] = { 04, (byte) 0x80, (byte) 0x80, 0x0C, 0x03, 0x14 };
        msgOut[0] = fix[0];
        msgOut[1] = fix[1];
        msgOut[2] = fix[2];
        msgOut[3] = fix[3];
        // fix[4]=dc->connectionNumber2; // 1/10/05 trying Andrew's patch
        // fix[5]=dc->connectionNumber; // 1/10/05 trying Andrew's patch
        msgOut[4] = fix[4];
        msgOut[5] = fix[5];
        msgOut[1] |= MPI;
        msgOut[6] = (byte) 0xF1;
        return mpiface.sendWithCRC(msgOut, size + fix.length);
    }

    /*
     * Open connection to a PLC. This assumes that dc is initialized by
     * daveNewConnection and is not yet used.
     * (or reused for the same PLC ?)
     */
    @Override
    public int connectPLC() throws IOException {
        int res;
        byte[] b4 = { 0x04, (byte) 0x80, (byte) 0x80, 0x0D, 0x00, 0x14, (byte) 0xE0, 0x04, 0x00, (byte) 0x80, 0x00,
                0x02, 0x00, 0x02, 0x01, 0x00, 0x01, 0x00, };
        int[] t4 = { 0x04, 0x80, 0x80, 0x0C, 0x14, 0x03, 0xD0, 0x04, 0x00, 0x80, 0x00, 0x02, 0x00, 0x02, 0x01, 0x00,
                0x01, 0x00, };
        byte[] b5 = { 0x05, 0x01, };
        int[] t5 = { 0x04, 0x80, 0x80, 0x0C, 0x14, 0x03, 0x05, 0x01, };
        b4[1] |= (byte) MPI;
        // b4[2] |= (byte)iface.localMPI;
        t4[1] |= (byte) MPI;
        // t4[2] |= (byte)iface.localMPI;
        t5[1] |= (byte) MPI;
        // t5[2] |= (byte)iface.localMPI;
        // t7[1] |= (byte)MPIadr;
        // t7[2] |= (byte)iface.localMPI;
        mpiface.sendSingle(STX);
        mpiface.initStep(1, b4, b4.length, "Connection");
        res = readMPI3();

        if (0 != MPIinterface.memcmp(t4, msgIn, t4.length)) {
            Nodave.dump("got:    ", msgIn, 0, res);
            mpiface.disconnectAdapter();
            return 3;
        }
        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("daveConnectPLC() step 3.");
        }
        mpiface.sendSingle(DLE);
        mpiface.sendSingle(STX);
        if (mpiface.readSingle() != DLE) {
            return 4;
        }
        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("daveConnectPLC() step 4.");
        }
        sendWithPrefix(b5, b5.length);
        if (mpiface.readSingle() != DLE) {
            return 5;
        }
        if (mpiface.readSingle() != STX) {
            return 5;
        }
        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("daveConnectPLC() step 5.");
        }
        mpiface.sendSingle(DLE);
        res = readMPI4();
        if (0 != MPIinterface.memcmp(t5, msgIn, t5.length)) {
            return 6;
        }
        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("daveConnectPLC() step 6.");
        }
        messageNumber = 0;

        negPDUlengthRequest();

        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("\n*** Partner offered PDU length: " + maxPDUlength);
        }
        return 0;
    }

    int readMPI3() throws IOException {
        return mpiface.readMPI(msgIn);
    }

    @Override
    public int disconnectPLC() throws IOException {
        int i, res;
        byte[] m = { (byte) 0x80 };
        i = sendDialog(m, m.length);
        mpiface.sendSingle(DLE);
        if (mpiface.readSingle() != STX) {
            return 1;
        }
        if ((Nodave.Debug & Nodave.DEBUG_CONNECT) != 0) {
            System.out.println("daveDisConnectPLC() step 6.");
        }
        res = readMPI3();
        mpiface.sendSingle(DLE);
        return 0;
    }

    int sendDialog(byte[] b, int size) throws IOException {
        mpiface.sendSingle(STX);
        if (mpiface.readSingle() != DLE) {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("*** no DLE before send.");
            }
            return -1;
        }
        if (size > 5) {
            if (messageNumber == 0) {
                messageNumber = 1;
            }
            b[1] = (messageNumber);
            b[ackPos] = (byte) ((messageNumber + 1) & 0xff);
            needAckNumber = messageNumber;
            messageNumber++;
            messageNumber &= 0xff; // !!
        }
        sendWithPrefix(b, size);
        if (mpiface.readSingle() != DLE) {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("*** no DLE after send.");
            }
            return -2;
        }
        return 0;
    }

    @Override
    public int getResponse() throws IOException {
        int res, i;
        res = 0;
        if ((Nodave.Debug & Nodave.DEBUG_EXCHANGE) != 0) {
            System.out.println("enter getMPIresponse");
        }
        i = mpiface.readMPI(msgIn);
        System.out.println("i:" + i);
        if (i == 1) {
            mpiface.sendSingle(DLE);
        }
        i = mpiface.readMPI2(msgIn);

        System.out.println("i:" + i);
        if ((Nodave.Debug & Nodave.DEBUG_RAWREAD) != 0) {
            System.out.println("i:" + i + " res:" + res);
        }
        if (i == 0) {
            return -1;
        }
        sendAck(msgIn[ackPos + 1]);
        res = mpiface.readSingle();
        if (res != DLE) {
            if ((Nodave.Debug & Nodave.DEBUG_PRINT_ERRORS) != 0) {
                System.out.println("*** getMPIresponse: no DLE after Ack. Got:" + res);
            }
            return 0;
        }
        return 0;
    }
}
