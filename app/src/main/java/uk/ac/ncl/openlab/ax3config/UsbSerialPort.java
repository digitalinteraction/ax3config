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

    // USB device serial number
    public String getSerialNumber() {
        return connection.getSerial();
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
            if (numRead <= 0) { break; }
            offset += numRead;
            if (single) {
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

    // Read line (note: any partially-written line is lost, but should only happen if device outgoing buffer was full)
    StringBuilder sb = new StringBuilder();
    public String[] readLines(int timeoutMS, boolean single) {
        byte[] buffer = new byte[64];
        List<String> lines = new ArrayList<String>();
        for (;;) {
            int count = read(buffer, timeoutMS, single); // blocking wait for next read (or timeout)
            // parse as characters, line-by-line
            for (int i = 0; i < count; i++) {
                char c = (char) buffer[i];
                if (c == '\n') {
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\r') {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    lines.add(sb.toString());
                    sb.delete(0, sb.length());
                } else {
                    sb.append(c);
                }
            }
            // return if last read was partial
            if (count < buffer.length) { break; }
        }
        return lines.toArray(new String[0]);
    }

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
