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

public class S7DateTime {
    byte[] buffer;
    int pos;

    S7DateTime(byte[] buffer, int pos) {
        this.buffer = buffer;
        this.pos = pos;
    }

    static String fromBCD2(int i) {
        String s = Integer.toHexString(i);
        if (s.length() < 2) {
            s = "0" + s;
        }
        return s;
    }

    public String getString() {
        int year = buffer[pos];
        if (year < 90) {
            year = year + 2000;
        } else {
            year = year + 1900;
        }
        String month = fromBCD2(buffer[pos + 1]);
        String day = fromBCD2(buffer[pos + 2]);
        String hour = fromBCD2(buffer[pos + 3]);
        String minute = fromBCD2(buffer[pos + 4]);
        String second = fromBCD2(buffer[pos + 5]);
        return day + '.' + month + '.' + year + ' ' + hour + ':' + minute + ':' + second;
    }

    public static String toString(byte[] buffer, int pos) {
        int year = buffer[pos];
        if (year < 90) {
            year = year + 2000;
        } else {
            year = year + 1900;
        }
        String month = fromBCD2(buffer[pos + 1]);
        String day = fromBCD2(buffer[pos + 2]);
        String hour = fromBCD2(buffer[pos + 3]);
        String minute = fromBCD2(buffer[pos + 4]);
        String second = fromBCD2(buffer[pos + 5]);
        return day + '.' + month + '.' + year + ' ' + hour + ':' + minute + ':' + second;
    }

}