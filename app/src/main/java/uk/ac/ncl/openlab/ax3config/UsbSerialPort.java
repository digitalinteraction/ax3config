package uk.ac.ncl.openlab.ax3config;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.IOException;

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
    public static UsbDevice findFirstDevice(UsbManager usbManager) {
        for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            if (UsbSerialPort.isDevice(usbDevice)) {
                return usbDevice;
            }
        }
        return null;
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

    // Read bytes into the buffer
    public int read(byte[] buffer, int timeoutMS) {
        // bulkTransfer does not support an offset, so use a local buffer
        byte[] readBuffer = new byte[1024];
        int offset = 0;
        while (offset < buffer.length) {
            int toRead = buffer.length - offset;
            if (toRead > readBuffer.length) {
                toRead = readBuffer.length;
            }
            int numRead = connection.bulkTransfer(endpointRead, readBuffer, toRead, timeoutMS);
            if (numRead < 0) { break; }
            System.arraycopy(readBuffer, 0, buffer, offset, numRead);
            offset += numRead;
        }
        return offset;
    }

    // Write bytes from the buffer
    public int write(byte[] buffer, int timeoutMS) {
        // bulkTransfer does not support an offset, so use a local buffer
        byte[] writeBuffer = new byte[1024];
        int offset = 0;
        while (offset < buffer.length) {
            int toWrite = buffer.length - offset;
            if (toWrite > writeBuffer.length) {
                toWrite = writeBuffer.length;
            }
            System.arraycopy(buffer, offset, writeBuffer, 0, toWrite);
            int numWritten = connection.bulkTransfer(endpointWrite, writeBuffer, toWrite, timeoutMS);
            if (numWritten < 0) { break; }
            offset += numWritten;
        }
        return offset;
    }

    @Override
    public String toString() {
        return "UsbSerialPort " + device.getDeviceName() + " " + device.getDeviceId();
    }

}
