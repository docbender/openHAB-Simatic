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
import java.io.InputStream;
import java.io.OutputStream;

public class MPIinterface extends PLCinterface {
    public static final byte STX = 0x2;
    public static final byte ETX = 0x3;
    public static final byte DLE = 0x10;

    public MPIinterface(OutputStream os, InputStream is, String name, int localMPI, int protocol) {
        init(os, is, name, localMPI, protocol);
        timeout = 1500;
    }

    /*
     * Send a string of init data to the MPI adapter.
     */
    int initStep(int nr, byte[] fix, int len, String initRoutine) throws IOException {
        int res;
        if (readSingle() != DLE) {
            if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
                System.out.println("init" + initRoutine + "() no answer (DLE) from adapter.");
            }
            return nr;
        }
        if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
            System.out.println("init" + initRoutine + "()  step " + nr);
        }
        sendWithCRC(fix, len);
        if (readSingle() != DLE) {
            return nr + 1;
        }
        if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
            System.out.println("init" + initRoutine + "()  step " + (nr + 1));
        }
        if (readSingle() != STX) {
            return nr + 2;
        }
        if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
            System.out.println("init" + initRoutine + "()  step " + (nr + 2));
        }
        sendSingle(DLE);
        return 0;
    }

    void sendSingle(byte c) throws IOException {
        byte[] b = { 0 };
        b[0] = c;
        write(b, 0, 1);
    }

    byte readSingle() throws IOException {
        byte[] b = { 0 };
        int res = read(b, 0, 1);
        if ((Nodave.Debug & Nodave.DEBUG_RAWREAD) != 0) {
            System.out.println("readSingle " + b[0]);
        }
        return b[0];
    }

    int sendWithCRC(byte[] b, // a buffer containing the message
            int size // the size of the string
    ) throws IOException {
        byte[] target = new byte[Nodave.MAX_RAW_LEN];
        int targetSize = 0, i;
        byte bcc = (byte) (DLE ^ ETX); // preload
        for (i = 0; i < size; i++) {
            target[targetSize] = b[i];
            targetSize++;
            if (DLE == b[i]) {
                target[targetSize] = DLE;
                targetSize++;
            } else {
                bcc = (byte) (bcc ^ b[i]);
                // The doubled DLE effectively contributes
                // nothing
            }
        }
        ;
        target[targetSize] = DLE;
        target[targetSize + 1] = ETX;
        target[targetSize + 2] = bcc;
        targetSize += 3;
        if ((Nodave.Debug & Nodave.DEBUG_RAWSEND) != 0) {
            Nodave.dump("sendWithCRC", target, 0, targetSize);
        }
        write(target, 0, targetSize);
        return 0;
    }

    /*
     * List reachable devices on this MPI net.
     */
    public int listReachablePartners(byte[] buf) throws IOException {
        byte[] b1 = new byte[Nodave.MAX_RAW_LEN];
        byte[] m1 = { 1, 7, 2 };
        int res;
        if ((Nodave.Debug & Nodave.DEBUG_LIST_REACHABLES) != 0) {
            System.out.println("daveListReachablePartners() step 1.");
        }
        sendSingle(STX);
        if (readSingle() != DLE) {
            return -1;
        }
        if ((Nodave.Debug & Nodave.DEBUG_LIST_REACHABLES) != 0) {
            System.out.println("daveListReachablePartners() step 2.");
        }

        sendWithCRC(m1, m1.length);
        if (readSingle() != DLE) {
            return -2;
        }
        if ((Nodave.Debug & Nodave.DEBUG_LIST_REACHABLES) != 0) {
            System.out.println("daveListReachablePartners() step 3.");
        }
        if (readSingle() != STX) {
            return -3;
        }
        if ((Nodave.Debug & Nodave.DEBUG_LIST_REACHABLES) != 0) {
            System.out.println("daveListReachablePartners() step 4.");
        }
        sendSingle(DLE);
        res = readMPI(b1);
        if ((Nodave.Debug & Nodave.DEBUG_LIST_REACHABLES) != 0) {
            System.out.println("daveListReachablePartners() step 5.");
        }
        sendSingle(DLE);
        if (buf != null) {
            System.arraycopy(b1, 6, buf, 0, Nodave.PartnerListSize);
        }
        return Nodave.PartnerListSize;
    };

    int readMPI(byte[] b) throws IOException {
        if ((Nodave.Debug & Nodave.DEBUG_RAWREAD) != 0) {
            System.out.println("readMPI");
        }
        int count = 0;
        byte bcc = 0;
        int state = 0;
        // int maxBytes=1;
        do {
            int res = read(b, count, 1);
            // if ((Nodave.Debug & Nodave.DEBUG_RAWREAD)!=0)
            // System.out.println("readMPI "+res+" count:"+count);
            if (res == 0) {
                System.out.println("readMPI timeout !");
                return (0);
            } else {
                // maxBytes=Nodave.MAX_RAW_LEN;
                count += res;
                if ((count == 1) && (b[0] == DLE)) {
                    if ((Nodave.Debug & Nodave.DEBUG_SPECIALCHARS) != 0) {
                        System.out.println("readMPI single DLE.");
                    }
                    return 1;
                }
                if ((count == 1) && (b[0] == STX)) {
                    if ((Nodave.Debug & Nodave.DEBUG_SPECIALCHARS) != 0) {
                        System.out.println("readMPI single STX.");
                    }
                    return 1;
                }
                if (b[count - 1] == DLE) {
                    if (state == 0) {
                        state = 1;
                        if ((Nodave.Debug & Nodave.DEBUG_SPECIALCHARS) != 0) {
                            System.out.println("readMPI 1st DLE in data.");
                        }
                    } else if (state == 1) {
                        state = 0;
                        count--; // forget this DLE
                        if ((Nodave.Debug & Nodave.DEBUG_SPECIALCHARS) != 0) {
                            System.out.println("readMPI 2nd DLE in data.");
                        }
                    }
                }
                if (state == 3) {
                    if ((Nodave.Debug & Nodave.DEBUG_SPECIALCHARS) != 0) {
                        System.out.println("readMPI: packet end, got BCC: " + b[count - 1] + ". I calc: " + bcc);
                    }
                    if ((Nodave.Debug & Nodave.DEBUG_RAWREAD) != 0) {
                        Nodave.dump("MPI packet: ", b, 0, count);
                    }
                    return count;
                } else {
                    bcc = (byte) (bcc ^ (b[count - 1]));
                }
                if (b[count - 1] == ETX) {
                    if (state == 1) {
                        state = 3;
                        if ((Nodave.Debug & Nodave.DEBUG_SPECIALCHARS) != 0) {
                            System.out.println("readMPI: DLE ETX,packet end.\n");
                        }
                    }
                }
            }
        } while (true);
    }

    @Override
    public int initAdapter() throws IOException {
        byte[] b2 = { 0x01, 0x0D, 0x02, };
        int[] answ1 = { 0x01, 0x0D, 0x20, 'V', '0', '0', '.', '8', '3' };
        int[] adapter0330 = { 0x01, 0x03, 0x20, 'E', '=', '0', '3', '3', '0' };

        byte[] b3 = { 0x01, 0x03, 0x02, 0x27, 0x00, (byte) 0x9F, 0x01, 0x3C, 0x00, (byte) 0x90, 0x01, 0x14, 0x00, 0x00,
                0x05, 0x02, 0x00, 0x1F, 0x02, 0x01, 0x01, 0x03, (byte) 0x80, };
        byte[] v1 = { 0x01, 0x0C, 0x02, };

        int res;
        byte[] b1 = new byte[Nodave.MAX_RAW_LEN];
        if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
            System.out.println("enter initAdapter().");
        }
        sendSingle(STX);
        res = initStep(1, b2, b2.length, "Adapter");
        if (res != 0) {
            if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
                System.out.println("initAdapter() fails.");
            }
            return -44;
        }

        res = readMPI2(b1);
        if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
            Nodave.dump("got:   ", b1, 0, res);
        }
        if (0 != memcmp(answ1, b1, answ1.length)) {
            if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
                Nodave.dump("got:   ", b1, 0, res);
            }
            return 4;
        }
        b3[16] = (byte) localMPI;
        // b3[b3.length - 1] = (byte) (b3[b3.length - 1] ^ localMPI);
        // 'patch' the checksum
        res = initStep(4, b3, b3.length, "Adapter");
        if (res != 0) {
            if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
                System.out.println("initAdapter() fails.");
            }
            return -54;
        }

        /*
         * The following extra lines seem to be necessary for
         * MPI adapter 6ES7 972-0CA33-0XAC:
         */
        res = readMPI(b1);
        sendSingle(DLE);
        if (0 == memcmp(adapter0330, b1, adapter0330.length)) {
            if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
                System.out.println("initAdapter() found Adapter E=0330.");
            }
            sendSingle(STX);
            res = readMPI2(b1);
            sendWithCRC(v1, v1.length);
            if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
                System.out.println("initAdapter() Adapter E=0330 step 7.");
            }
            if (readSingle() != DLE) {
                return 8;
            }
            if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
                System.out.println("initAdapter() Adapter E=0330 step 8.");
            }
            res = readMPI(b1);
            if (res != 1 || b1[0] != STX) {
                return 9;
            }
            // if (readSingle() != STX)
            // return 9;
            if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
                System.out.println("initAdapter() Adapter E=0330 step 9.");
            }
            sendSingle(DLE);
            /* This needed the exact Adapter version: */
            /* instead just read and waste it */
            res = readMPI(b1);
            if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
                System.out.println("initAdapter() Adapter E=0330 step 10.");
            }
            sendSingle(DLE);
            return 0;
        }
        res = readMPI2(b1);
        if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
            System.out.println("initAdapter() success.");
        }
        sendSingle(DLE);
        return 0;
    }

    int readMPI2(byte[] b) throws IOException {
        int res = readMPI(b);
        if (res > 1) {
            sendSingle(DLE);
            sendSingle(STX);
        }
        return res;
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
    public int disconnectAdapter() throws IOException {
        System.out.println("enter disconnectAdapter()");
        byte[] m2 = { 1, 4, 2 };
        byte[] b1 = new byte[Nodave.MAX_RAW_LEN];
        int res;
        if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
            System.out.println("disconnectAdapter() step 0.");
        }
        sendSingle(STX);
        if (readSingle() != DLE) {
            ; // return 1;
        }
        sendWithCRC(m2, m2.length);
        if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
            System.out.println("disconnectAdapter() step 1.");
        }
        if (readSingle() != DLE) {
            return 2;
        }
        readMPI(b1);
        if (readSingle() != STX) {
            return 3;
        }
        if ((Nodave.Debug & Nodave.DEBUG_INITADAPTER) != 0) {
            System.out.println("disconnectAdapter() step 2.");
        }
        sendSingle(DLE);
        readMPI(b1);
        sendSingle(DLE);
        return 0;
    }

}
