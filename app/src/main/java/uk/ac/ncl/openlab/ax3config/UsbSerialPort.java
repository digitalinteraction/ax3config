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

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class UsbSerialPort {

    // 0x04D8 / 0x0057: Microchip USB Composite MSD+CDC Device
    private static final int PRODUCT_VID = 0x04D8;
    private static final int PRODUCT_PID = 0x0057;

    // Device connection
    protected UsbDevice device;
    protected UsbDeviceConnection connection = null;

    // Interfaces/endpoints
    private UsbInterface interfaceControl;
    private UsbInterface interfaceData;
    private UsbEndpoint endpointControl;
    private UsbEndpoint endpointRead;
    private UsbEndpoint endpointWrite;


    // Find the first device present
    public static UsbDevice[] getDevices(UsbManager usbManager) {
        List<UsbDevice> devices = new ArrayList<UsbDevice>();
        for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            if (UsbSerialPort.isDevice(usbDevice)) {
                devices.add(usbDevice);
            }
        }
        return devices.toArray(new UsbDevice[0]);
    }

    // Determines whether a UsbDevice is the correct VID/PID
    public static boolean isDevice(UsbDevice usbDevice) {
        return usbDevice.getVendorId() == PRODUCT_VID && usbDevice.getProductId() == PRODUCT_PID;
    }

    // Construct against a device
    public UsbSerialPort(UsbDevice device) {
        this.device = device;
    }

    // Open a connection
    public void open(UsbManager usbManager) throws IOException {
        // Close any existing connection
        close();

        // Open a connection to the device
        UsbDeviceConnection connection = usbManager.openDevice(this.device);
        if (connection == null) {
            throw new IOException("Problem opening device.");
        }

        // Find control and data interfaces
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface usbInterface = device.getInterface(i);
            if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_COMM) {
                interfaceControl = usbInterface;
            }
            if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA) {
                interfaceData = usbInterface;
            }
        }
        if (interfaceControl == null || interfaceData == null) {
            throw new IOException("Could not find control and data interfaces.");
        }

        // Claim control interface (although it is not used)
        if (!connection.claimInterface(interfaceControl, true)) {
            throw new IOException("Problem claiming control interface.");
        }
        // Find control endpoint
        for (int i = 0; i < interfaceControl.getEndpointCount(); i++) {
            UsbEndpoint endpoint = interfaceControl.getEndpoint(i);
            if (endpoint.getDirection() == UsbConstants.USB_DIR_IN && endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                endpointControl = endpoint;
                break;
            }
        }
        if (endpointControl == null) {
            throw new IOException("Could not find control endpoint.");
        }

        // Claim data interface
        if (!connection.claimInterface(interfaceData, true)) {
            throw new IOException("Problem claiming data interface.");
        }
        // Find read/write endpoints
        for (int i = 0; i < interfaceData.getEndpointCount(); i++) {
            UsbEndpoint endpoint = interfaceData.getEndpoint(i);
            if (endpoint.getDirection() == UsbConstants.USB_DIR_IN && endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                endpointRead = endpoint;
            }
            if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT && endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                endpointWrite = endpoint;
            }
        }
        if (endpointRead == null || endpointWrite == null) {
            throw new IOException("Could not find read/write data endpoints.");
        }

        this.connection = connection;
    }

    // Close the connection
    public void close() {
        if (this.connection != null) {
            if (endpointControl != null) {
                connection.releaseInterface(interfaceControl);
                endpointControl = null;
            }
            if (endpointWrite != null || endpointRead != null) {
                connection.releaseInterface(interfaceData);
                endpointWrite = null;
                endpointRead = null;
            }
            this.connection.close();
            this.connection = null;
        }
    }

    @Override
    public void finalize() {
        close();
    }

    // USB device serial number
    public int getSerialNumber() {
        String serial = connection.getSerial().trim();
        if (!serial.startsWith("AX") && !serial.startsWith("CWA"))
            return -1;
        for (int i = serial.length() - 1; ; i--) {
            if (i < 0 || !Character.isDigit(serial.charAt(i))) {
                if (i + 1 >= serial.length()) {
                    return -1;
                }
                return Integer.parseInt(serial.substring(i + 1));
            }
        }
    }

    // Read bytes
    public int read(byte[] buffer, int timeoutMS) {
        return read(buffer, timeoutMS, false);
    }
    public int read(byte[] buffer, int timeoutMS, boolean single) {
        // UsbConnection.bulkTransfer() before JELLY_BEAN_MR2 does not support an offset, so use a local buffer
        byte[] readBuffer = null;
        int offset = 0;
        while (offset < buffer.length) {
            int toRead = buffer.length - offset;
            int numRead;
            if (offset == 0) {
                numRead = connection.bulkTransfer(endpointRead, buffer, toRead, timeoutMS);
            } else {
                if (readBuffer == null || readBuffer.length < toRead) {
                    readBuffer = new byte[toRead];
                }
                numRead = connection.bulkTransfer(endpointRead, readBuffer, toRead, timeoutMS);
                if (numRead > 0) {
                    System.arraycopy(readBuffer, 0, buffer, offset, numRead);
                }
            }
            if (numRead < 0) { break; } // time-out
            offset += numRead;
            if (single && numRead != 0) {
                break;
            }
        }
        return offset;
    }

    // Write bytes
    public int write(byte[] buffer, int timeoutMS) {
        // UsbConnection.bulkTransfer() before JELLY_BEAN_MR2 does not support an offset, so use a local buffer
        byte[] writeBuffer = null;
        int offset = 0;
        while (offset < buffer.length) {
            int toWrite = buffer.length - offset;
            int numWritten;
            if (offset == 0) {
                numWritten = connection.bulkTransfer(endpointWrite, buffer, toWrite, timeoutMS);
            } else {
                if (writeBuffer == null || writeBuffer.length < toWrite) {
                    writeBuffer = new byte[toWrite];
                }
                System.arraycopy(buffer, offset, writeBuffer, 0, toWrite);
                numWritten = connection.bulkTransfer(endpointWrite, writeBuffer, toWrite, timeoutMS);
            }
            if (numWritten <= 0) { break; }
            offset += numWritten;
        }
        return offset;
    }

    // Read line, up to one beginning with a final prefix, or timeouts
    StringBuilder sb = new StringBuilder();
    public String[] readLines(int initialTimeoutMs, int continuationTimeoutMs, String finalPrefix) {
        boolean endNow = false;
        boolean probableEnd = false;
        byte[] buffer = new byte[64];
        List<String> lines = new ArrayList<String>();
        for (;;) {
            if (endNow) break;
            int count = read(buffer, probableEnd ? continuationTimeoutMs : initialTimeoutMs, true); // blocking wait for next read (or timeout)
            // parse as characters, line-by-line
            for (int i = 0; i < count; i++) {
                char c = (char) buffer[i];
                if (c == '\n') {
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\r') {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    String newLine = sb.toString();
                    lines.add(newLine);
                    sb.delete(0, sb.length());
                    if (finalPrefix != null && newLine.startsWith(finalPrefix)) {
                        endNow = true;  // but continue processing bytes
                    }
                } else {
                    sb.append(c);
                }
            }
            // if last read was partial...
            if (count < buffer.length) {
                if (count == 0 || probableEnd) break;

                // if was also at the end of a string, probably the end
                if (sb.length() == 0) probableEnd = true;
                else probableEnd = false;
            }
        }
        return lines.toArray(new String[0]);
    }

/*
    // Read string
    public String readString(int numBytes, int timeoutMS) {
        byte[] buffer = new byte[numBytes];
        int count = read(buffer, timeoutMS);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            char c = (char)buffer[i];
            sb.append(c);
        }
        return sb.toString();
    }
*/

    // Write string
    public boolean writeString(String str, int timeoutMS) {
        byte[] outBuffer = str.getBytes(Charset.forName("UTF-8"));
        int numBytesWritten = write(outBuffer, timeoutMS);
        return numBytesWritten == outBuffer.length;
    }

    @Override
    public String toString() {
        return "UsbSerialPort " + device.getDeviceName() + " " + device.getDeviceId();
    }

}
