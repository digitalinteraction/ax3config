/*
* Copyright (c) 2018, Newcastle University, UK.
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
* 1. Redistributions of source code must retain the above copyright notice,
*    this list of conditions and the following disclaimer.
* 2. Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
* LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
* CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
* ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE.
*/

// USB Serial Port I/O for Open Movement AX3 Device
// Dan Jackson, 2018

package uk.ac.ncl.openlab.ax3config;

import java.io.IOException;
import java.util.Date;
import java.text.SimpleDateFormat;

public class AxConfig {

    // Device connection
    protected UsbSerialPort serialPort;

    protected final String DATE_FORMAT = "yyyy/MM/dd','HH:mm:ss";

    // Construct against a device
    public AxConfig(UsbSerialPort serialPort) {
        this.serialPort = serialPort;
    }

    protected String[] command(String command, String expectedPrefix) throws IOException {
        if (!serialPort.writeString(command + "\r\n", 500)) {
            throw new IOException("Problem sending command");
        }
        String[] lines = serialPort.readLines(1000, 250, expectedPrefix);
        if (lines.length <= 0) {
            throw new IOException("No response");
        }
        if (!lines[lines.length - 1].startsWith(expectedPrefix)) {
            throw new IOException("Expected response not received: " + lines[lines.length - 1] + " -- expecting: " + expectedPrefix);
        }
        String responseLine = lines[lines.length - 1].trim();
        return responseLine.split("[\\:= ]");
    }

    public void setSessionId(int value) throws IOException {
        command("SESSION " + value, "SESSION=" + value);
    }

    public void setStartTime(Date startTime) throws IOException {
        // "HIBERNATE=2018/07/16,16:00:00"
        String timeString = (new SimpleDateFormat(DATE_FORMAT)).format(startTime);
        command("HIBERNATE " + timeString, "HIBERNATE=" + timeString);
    }

    public void setEndTime(Date endTime) throws IOException {
        // "STOP=2018/07/17,09:00:00""
        String timeString = (new SimpleDateFormat(DATE_FORMAT)).format(endTime);
        command("STOP " + timeString, "STOP=" + timeString);
    }

    public void setRate(int rate, int range) throws IOException {
        // "RATE=74,100"
        int value = 0;

        switch (rate)
        {
            case 3200: value |= 0x0f; break;
            case 1600: value |= 0x0e; break;
            case  800: value |= 0x0d; break;
            case  400: value |= 0x0c; break;
            case  200: value |= 0x0b; break;
            case  100: value |= 0x0a; break;
            case   50: value |= 0x09; break;
            case   25: value |= 0x08; break;
            case   12: value |= 0x07; break;
            case    6: value |= 0x06; break;
            default: throw new IOException("Invalid rate");
        }

        switch (range)
        {
            case 16: value |= 0x00; break;
            case  8: value |= 0x40; break;
            case  4: value |= 0x80; break;
            case  2: value |= 0xC0; break;
            default: throw new IOException("Invalid range");
        }

        command("RATE " + value, "RATE=" + value + "," + rate);
    }

    public void setTime(Date time) throws IOException {
        // "$TIME=2000/01/01,00:01:22"
        String timeString = (new SimpleDateFormat(DATE_FORMAT)).format(time);
        command("TIME " + timeString, "$TIME=" + timeString);
    }

    public void commit() throws IOException {
        command("COMMIT", "COMMIT: Delayed activation.");
    }

    public void setLed(int value) throws IOException {
        command("LED " + value, "LED=" + value);
    }

    /*
        String cmd = "SESSION";
        String[] results = command(cmd + " " + value, cmd + "=");
        if (results.length < 2 || results[0] != cmd) throw new IOException("Unexpected response");
        int setValue;
        try {
            setValue = Integer.parseInt(results[1], 10);
        } catch (NumberFormatException e) { throw new IOException("Invalid response value"); }
        if (setValue != value) { throw new IOException("Invalid response value"); }
     */

    @Override
    public void finalize() {
        close();
    }

    public void close() {
        if (serialPort != null) {
            serialPort.close();
            serialPort = null;
        }
    }

}
