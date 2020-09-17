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

/**
 * @author Thomas Hergenhahn
 *
 */
public class ResultSet {
    private int errorState, numResults;
    public Result[] results;

    public void setErrorState(int error) {
        errorState = error;
    }

    public int getErrorState() {
        return errorState;
    };

    public void setNumResults(int nr) {
        numResults = nr;
    }

    public int getNumResults() {
        return numResults;
    };
}
