# AX3-Config

Android AX3 Configuration Tool

## Application

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
