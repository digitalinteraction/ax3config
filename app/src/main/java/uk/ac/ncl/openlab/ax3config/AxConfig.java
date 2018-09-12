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
import java.util.Locale;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.text.ParseException;

public class AxConfig {

    // AX3 date range is 2000-01-01T00:00:00 to 2063-12-31T23:59:59
    private int MIN_YEAR = 2000;
    private int MAX_YEAR = 2063;
    private final String DATE_FORMAT = "yyyy/MM/dd','HH:mm:ss";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.US);

    // Device connection
    private UsbSerialPort serialPort;


    // Construct against a device
    public AxConfig(UsbSerialPort serialPort) {
        this.serialPort = serialPort;
    }

    private String dateToString(Date time, boolean clamp) {
        String timeString;
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
        if (clamp && cal.get(Calendar.YEAR) < MIN_YEAR) {
            return "0";
        } else if (clamp && cal.get(Calendar.YEAR) > MAX_YEAR) {
            return "-1";
        } else {
            return dateFormat.format(time);
        }
    }

    private Date stringToDate(String input) {
        if (input.equals("0")) {
            return new Date(Long.MIN_VALUE);
        } else if (input.equals("-1")) {
            return new Date(Long.MAX_VALUE);
        } else {
            try {
                return dateFormat.parse(input);
            } catch (ParseException e) {
                return null;
            }
        }
    }

    private String[] command(String command, String expectedPrefix, boolean commaSeparated) throws IOException {
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
        for (int i = 0; i < responseLine.length(); i++) {
            char c = responseLine.charAt(i);
            if (c == ':' || c == ' ' || c == '=' || c == ',') {
                String key = responseLine.substring(0, i);
                String value = responseLine.substring(i + 1);
                if (commaSeparated) {
                    // Parsed comma/space separated values
                    String[] parts = value.split("[, ]");
                    String[] retParts = new String[1 + parts.length];
                    retParts[0] = key;
                    for (int j = 0; j < parts.length; j++) {
                        retParts[1 + j] = parts[j];
                    }
                    return retParts;
                } else {
                    // Parsed name/value pair
                    return new String[] { key, value };
                }
            }
        }
        // Unparsed: return string
        return new String[] { responseLine };
    }



    public void setSessionId(int value) throws IOException {
        command("SESSION " + value, "SESSION=" + value, true);
    }

    public int getSessionId() throws IOException {
        // "SESSION=<sessionId>"
        String[] results = command("SESSION", "SESSION=", true);
        if (results.length < 2) throw new IOException("Unexpected response");
        try {
            return (int)Long.parseLong(results[1], 10);
        } catch (NumberFormatException e) { throw new IOException("Invalid response value"); }
    }

    public void setStartTime(Date startTime) throws IOException {
        // "HIBERNATE=2018/07/16,16:00:00"
        String timeString = dateToString(startTime, true);
        command("HIBERNATE " + timeString, "HIBERNATE=" + timeString, false);
    }

    public Date getStartTime() throws IOException {
        // "HIBERNATE=2018/07/16,16:00:00"
        String[] results = command("HIBERNATE", "HIBERNATE=", false);
        if (results.length < 2) throw new IOException("Unexpected response");
        try {
            String timeString;
            if (results.length > 2) {
                timeString = results[1] + "," + results[2];
            } else {
                timeString = results[1];
            }
            Date value = stringToDate(timeString);
            if (value == null) {
                throw new IOException("Could not parse time " + timeString);
            }
            return value;
        } catch (NumberFormatException e) { throw new IOException("Invalid response value"); }
    }

    public void setEndTime(Date endTime) throws IOException {
        // "STOP=2018/07/17,09:00:00""
        String timeString = dateToString(endTime, true);
        command("STOP " + timeString, "STOP=" + timeString, false);
    }

    public Date getEndTime() throws IOException {
        // "STOP=2018/07/17,09:00:00""
        String[] results = command("STOP", "STOP=", false);
        if (results.length < 2) throw new IOException("Unexpected response");
        try {
            String timeString;
            if (results.length > 2) {
                timeString = results[1] + "," + results[2];
            } else {
                timeString = results[1];
            }
            Date value = stringToDate(timeString);
            if (value == null) {
                throw new IOException("Could not parse time " + timeString);
            }
            return value;
        } catch (NumberFormatException e) { throw new IOException("Invalid response value"); }
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

        command("RATE " + value, "RATE=" + value + "," + rate, true);
    }

    public void setTime(Date time) throws IOException {
        // "$TIME=2000/01/01,00:01:22"
        String timeString = dateToString(time, false);
        command("TIME " + timeString, "$TIME=" + timeString, false);
    }

    public void commit(boolean wipe) throws IOException {
        // command("COMMIT", "COMMIT: Delayed activation.");
        if (wipe) {
            command("FORMAT WC", "FORMAT: Delayed activation.", false);
        } else {
            command("FORMAT QC", "FORMAT: Delayed activation.", false);
        }
    }

    public void setLed(int value) throws IOException {
        command("LED " + value, "LED=" + value, true);
    }

    public int getBattery() throws IOException {
        // "$BATT=<raw-ADC>,<millivolts>,mV,<percentage>,<charge-termination-flag>"
        String[] results = command("SAMPLE 1", "$BATT=", true);
        if (results.length < 5) throw new IOException("Unexpected response");
        try {
            return Integer.parseInt(results[4], 10);
        } catch (NumberFormatException e) { throw new IOException("Invalid response value"); }
    }

    public boolean hasConfiguration() throws IOException {
        int sessionId = getSessionId();
        Date startTime = getStartTime();
        Date endTime = getEndTime();
        return sessionId != 0 || endTime.getTime() > startTime.getTime();
    }

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
