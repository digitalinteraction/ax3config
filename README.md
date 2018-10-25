# AX3-Config

Android AX3 Configuration Tool

## Requirements

Android USB devices are normally the USB *device* that the *host* connects to. 
Most recent Android devices support "USB On-the-Go", where they can become the USB host. 
You will typically need to connect a USB male C or micro-USB male A on-the-go adapter to a standard USB female A socket. 
Then connect a standard USB A male to micro-USB A male connector to connect to an AX3.


## Application

The top most text area shows a log of activity. 
The upper of the two smaller boxes is for a numeric Session ID (up to 9 digits).
The lower box is for a custom command direct to the device (and should not be normally used).
On pressing *SEND*, any custom command will be sent to the device otherwise, if none is specified, the device will be configured. 
The configuration will only proceed if the device has at least 80% battery. 
You will be warned if there is an existing configuration that is being overwritten (normally, the configuration would be aborted if so). 
The configuration will be for the specified Session ID, 100 Hz sample rate and +/-8g sensitivity, start time of now, end time of 7 days from now, and the sensor time will be synchronized to the current local time. 
Once the device is configured, the LED will be lit as magenta to show completion. 


## Use in your own application

Should respond to the `android.hardware.usb.action.USB_DEVICE_ATTACHED` intent, and specify a filter:

```xml
<meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" android:resource="@xml/device_filter" />
```

...with a `device_filter.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 0x04D8 / 0x0057: Microchip USB Composite MSD+CDC Device -->
    <usb-device vendor-id="1240" product-id="87" />
</resources>
```

**UsbManager:** The application will need to `getSystemService(Context.USB_SERVICE)` to obtain a `UsbManager` reference.

**Discover:** Obtain the list of connected devices with `UsbSerialPort.getDevices(usbManager)`.

**Obtain Permission:** Use `PendingIntent.getBroadcast()` for the `com.android.example.USB_PERMISSION`, then `usbManager.requestPermission(usbDevice, permissionIntent)`.  When the permission is granted, you may connect to the device.

**Connect:** You can connect to the device as follows:
```java
// UsbDevice device = ...;
UsbSerialPort port = new UsbSerialPort(device);
try {
  port.open(usbManager);
  AxConfig config = new AxConfig(port);  
  // ...(other connected actions here)...
} finally {
  port.close();
}
```

**Device ID:** The device ID is available with `port.getSerialNumber()`.

**Existing Configuration:** To check whether the device has an existing configuration use `config.hasConfiguration()`.  It is recommended not to configure a device with an existing configuration (as any existing data will be lost).

**Battery Level:** Check the devices battery level (percent) `config.getBattery()` -- as an example, for 7-day recordings, it is recommended not to configure a device which has less than 80% battery.

**Configuring:** Configure the device:
```java
config.setSessionId(id);            // Set the session id (9 digits numeric)
config.setStartTime(start);         // Start date (and time)
config.setEndTime(end);             // End date (and time)
config.setRate(100, 8);             // 100 Hz, +/- 8g
config.setTime(now.time);           // Sync. the time with local device
config.commit(false);               // Commit the settings
config.setLed(5);                   // Set the LED to indicate completion (5=Magenta)
```

---

Manual install of a test `.apk`, either from the pre-build demo in `demo`, or `app/build/outputs/apk/debug`: `adb push app-debug.apk /data/local/tmp && adb shell pm install -t /data/local/tmp/app-debug.apk`
