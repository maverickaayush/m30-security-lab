#!/usr/bin/env python3
"""Bounded BlueZ GATT fixture for the owned M30 lab.

Adapted to the project's two UUIDs from BlueZ's public D-Bus examples.
No pairing, filesystem writes, or non-lab advertisement data.
"""
import argparse
import json
import time
import signal
import dbus
import dbus.service
import dbus.mainloop.glib
from gi.repository import GLib

BLUEZ = "org.bluez"
PROPS = "org.freedesktop.DBus.Properties"
OM = "org.freedesktop.DBus.ObjectManager"
SVC = "org.bluez.GattService1"
CHR = "org.bluez.GattCharacteristic1"
ADV = "org.bluez.LEAdvertisement1"
SERVICE_UUID = "7f5f42d6-4659-4c15-9fe7-7de7a7033b01"
CHAR_UUID = "7f5f42d6-4659-4c15-9fe7-7de7a7033b02"
BASE = "/org/cattail/m30lab"
RUN_ID = "manual"

def event(name, **fields):
    print(json.dumps({"run_id": RUN_ID, "event": name, "utc_ms": time.time_ns() // 1000000,
                      "mono_ns": time.monotonic_ns(), **fields}), flush=True)


class Properties(dbus.service.Object):
    iface = ""

    @dbus.service.method(PROPS, in_signature="s", out_signature="a{sv}")
    def GetAll(self, interface):
        if interface != self.iface:
            raise dbus.exceptions.DBusException("Unknown interface")
        return self.properties()


class Service(Properties):
    iface = SVC

    def __init__(self, bus):
        super().__init__(bus, BASE + "/service0")

    def properties(self):
        return {"UUID": dbus.String(SERVICE_UUID), "Primary": dbus.Boolean(True), "Characteristics": dbus.Array([dbus.ObjectPath(BASE + "/service0/char0")], signature="o")}


class Characteristic(Properties):
    iface = CHR

    def __init__(self, bus):
        super().__init__(bus, BASE + "/service0/char0")
        self.value = bytearray(b"LAB")
        self.notifying = False

    def properties(self):
        return {"UUID": dbus.String(CHAR_UUID), "Service": dbus.ObjectPath(BASE + "/service0"),
                "Flags": dbus.Array(["read", "write", "notify"], signature="s"),
                "Value": dbus.Array([dbus.Byte(x) for x in self.value], signature="y")}

    @dbus.service.method(CHR, in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        print("READ", bytes(self.value).hex(), flush=True)
        return dbus.Array([dbus.Byte(x) for x in self.value], signature="y")

    @dbus.service.method(CHR, in_signature="aya{sv}", out_signature="")
    def WriteValue(self, value, options):
        if len(value) > 20:
            raise dbus.exceptions.DBusException("org.bluez.Error.InvalidValueLength")
        self.value = bytearray(value)
        print("WRITE", bytes(self.value).hex(), flush=True)
        self.emit_value()

    @dbus.service.method(CHR, in_signature="", out_signature="")
    def StartNotify(self):
        self.notifying = True
        print("NOTIFY start", flush=True)
        self.emit_value()

    @dbus.service.method(CHR, in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False
        print("NOTIFY stop", flush=True)

    @dbus.service.signal(PROPS, signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass

    def emit_value(self):
        if self.notifying:
            self.PropertiesChanged(CHR, {"Value": dbus.Array([dbus.Byte(x) for x in self.value], signature="y")}, [])


class Application(dbus.service.Object):
    def __init__(self, bus, service, characteristic):
        super().__init__(bus, BASE)
        self.service = service
        self.characteristic = characteristic

    @dbus.service.method(OM, out_signature="a{oa{sa{sv}}}")
    def GetManagedObjects(self):
        return {dbus.ObjectPath(BASE + "/service0"): {SVC: self.service.properties()},
                dbus.ObjectPath(BASE + "/service0/char0"): {CHR: self.characteristic.properties()}}


class Advertisement(Properties):
    iface = ADV

    def __init__(self, bus):
        super().__init__(bus, BASE + "/advertisement0")

    def properties(self):
        return {"Type": dbus.String("peripheral"), "ServiceUUIDs": dbus.Array([SERVICE_UUID], signature="s"),
                "Discoverable": dbus.Boolean(True), "Duration": dbus.UInt16(0), "Timeout": dbus.UInt16(0)}

    @dbus.service.method(ADV, in_signature="", out_signature="")
    def Release(self):
        print("Advertisement released", flush=True)


def main():
    global RUN_ID
    parser = argparse.ArgumentParser()
    parser.add_argument("--seconds", type=int, default=90)
    parser.add_argument("--run-id", default="manual")
    args = parser.parse_args()
    RUN_ID = args.run_id
    seconds = max(10, min(args.seconds, 120))
    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()
    objects = dbus.Interface(bus.get_object(BLUEZ, "/"), OM).GetManagedObjects()
    adapter_path = next((p for p, v in objects.items() if "org.bluez.GattManager1" in v and "org.bluez.LEAdvertisingManager1" in v), None)
    if not adapter_path:
        raise SystemExit("No BlueZ adapter with GATT and LE advertising managers")
    service = Service(bus)
    char = Characteristic(bus)
    app = Application(bus, service, char)
    adv = Advertisement(bus)
    manager = dbus.Interface(bus.get_object(BLUEZ, adapter_path), "org.bluez.GattManager1")
    advertiser = dbus.Interface(bus.get_object(BLUEZ, adapter_path), "org.bluez.LEAdvertisingManager1")
    loop = GLib.MainLoop()
    signal.signal(signal.SIGINT, lambda *_: loop.quit())
    signal.signal(signal.SIGTERM, lambda *_: loop.quit())
    def fail(error):
        print("Registration failed:", error, flush=True)
        loop.quit()
    def registered(_=None):
        event("gatt_registered")
        advertiser.RegisterAdvertisement(dbus.ObjectPath(BASE + "/advertisement0"), {},
            reply_handler=lambda: event("advertisement_registered", service_uuid=SERVICE_UUID), error_handler=fail)
    manager.RegisterApplication(dbus.ObjectPath(BASE), {}, reply_handler=registered, error_handler=fail)
    GLib.timeout_add_seconds(seconds, lambda: (loop.quit(), False)[1])
    try:
        loop.run()
    finally:
        try: advertiser.UnregisterAdvertisement(dbus.ObjectPath(BASE + "/advertisement0"))
        except dbus.DBusException: pass
        try: manager.UnregisterApplication(dbus.ObjectPath(BASE))
        except dbus.DBusException: pass
        event("fixture_stopped")


if __name__ == "__main__":
    main()
