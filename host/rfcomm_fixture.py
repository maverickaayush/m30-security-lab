#!/usr/bin/env python3
"""Temporary, paired-device RFCOMM fixture registered with BlueZ SDP."""

import argparse
import json
import os
import signal
import socket
import threading
import time
import uuid
from datetime import datetime, timezone

import dbus
import dbus.service
from dbus.mainloop.glib import DBusGMainLoop
from gi.repository import GLib

UUID = "7f5f42d6-4659-4c15-9fe7-7de7a7033b02"
PATH = "/org/cattail/m30lab/rfcomm_fixture"
REQUEST = b"M30LAB/1 PING\n"
RESPONSE = b"M30LAB/1 PONG\n"
SDP_RECORD = f'''<?xml version="1.0" encoding="UTF-8" ?>
<record>
  <attribute id="0x0001"><sequence><uuid value="{UUID}" /></sequence></attribute>
  <attribute id="0x0004"><sequence>
    <sequence><uuid value="0x0100" /></sequence>
    <sequence><uuid value="0x0003" /><uint8 value="0x16" /></sequence>
  </sequence></attribute>
  <attribute id="0x0005"><sequence><uuid value="0x1002" /></sequence></attribute>
  <attribute id="0x0009"><sequence><sequence>
    <uuid value="0x1101" /><uint16 value="0x0100" />
  </sequence></sequence></attribute>
  <attribute id="0x0100"><text value="M30 LAB RFCOMM fixture" /></attribute>
</record>'''


class Logger:
    def __init__(self, path, run_id):
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        self.file = os.fdopen(descriptor, "w", buffering=1)
        self.lock = threading.Lock()
        self.run_id = run_id

    def event(self, kind, **data):
        record = {"timestamp_utc": datetime.now(timezone.utc).isoformat(),
                  "monotonic_ns": time.monotonic_ns(), "run_id": self.run_id,
                  "event": kind, **data}
        with self.lock:
            self.file.write(json.dumps(record, sort_keys=True) + "\n")
        print(json.dumps(record, sort_keys=True), flush=True)


def exchange(sock, logger, device):
    try:
        sock.settimeout(6.0)
        data = bytearray()
        while len(data) < 64:
            chunk = sock.recv(1)
            if not chunk:
                break
            data.extend(chunk)
            if chunk == b"\n":
                break
        logger.event("received", device=device, bytes_hex=bytes(data).hex(), recognized=bytes(data) == REQUEST)
        if bytes(data) == REQUEST:
            sock.sendall(RESPONSE)
            logger.event("response", device=device, bytes_hex=RESPONSE.hex())
        else:
            logger.event("rejected", device=device, reason="unexpected_request")
    except (OSError, TimeoutError) as exc:
        logger.event("socket_error", device=device, error=type(exc).__name__)
    finally:
        sock.close()
        logger.event("disconnected", device=device)


class Profile(dbus.service.Object):
    def __init__(self, bus, logger):
        super().__init__(bus, PATH)
        self.logger = logger
        self.sockets = set()
        self.lock = threading.Lock()

    @dbus.service.method("org.bluez.Profile1", in_signature="", out_signature="")
    def Release(self):
        self.logger.event("profile_released")

    @dbus.service.method("org.bluez.Profile1", in_signature="", out_signature="")
    def Cancel(self):
        self.logger.event("profile_cancelled")

    @dbus.service.method("org.bluez.Profile1", in_signature="oha{sv}", out_signature="")
    def NewConnection(self, device, fd, properties):
        sock = socket.socket(fileno=fd.take())
        self.logger.event("connected", device=str(device))
        with self.lock:
            self.sockets.add(sock)
        def run():
            try:
                exchange(sock, self.logger, str(device))
            finally:
                with self.lock:
                    self.sockets.discard(sock)
        threading.Thread(target=run, name="rfcomm-fixture-connection", daemon=True).start()

    @dbus.service.method("org.bluez.Profile1", in_signature="o", out_signature="")
    def RequestDisconnection(self, device):
        self.logger.event("disconnect_requested", device=str(device))
        with self.lock:
            for sock in tuple(self.sockets):
                try:
                    sock.close()
                except OSError:
                    pass

    def close_all(self):
        with self.lock:
            for sock in tuple(self.sockets):
                try:
                    sock.close()
                except OSError:
                    pass


def self_test():
    class Record:
        def __init__(self): self.events = []
        def event(self, kind, **data): self.events.append((kind, data))
    server, client = socket.socketpair()
    record = Record()
    worker = threading.Thread(target=exchange, args=(server, record, "self-test"))
    worker.start()
    client.sendall(REQUEST)
    assert client.recv(64) == RESPONSE
    client.close()
    worker.join(timeout=2)
    assert not worker.is_alive()
    assert [name for name, _ in record.events] == ["received", "response", "disconnected"]
    print("fixture self-test PASS")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", help="new private JSONL path")
    parser.add_argument("--max-seconds", type=int, default=900)
    parser.add_argument("--run-id", default=None, help="correlation ID for this fixture run")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if not args.log or not 60 <= args.max_seconds <= 1800:
        parser.error("--log and --max-seconds 60..1800 required")
    DBusGMainLoop(set_as_default=True)
    logger = Logger(args.log, args.run_id or str(uuid.uuid4()))
    bus = dbus.SystemBus()
    adapter_path = bus.get_object("org.bluez", "/org/bluez/hci0")
    props = dbus.Interface(adapter_path, "org.freedesktop.DBus.Properties")
    initial = {key: props.Get("org.bluez.Adapter1", key) for key in
               ("Discoverable", "Pairable", "DiscoverableTimeout", "PairableTimeout")}
    manager = dbus.Interface(bus.get_object("org.bluez", "/org/bluez"), "org.bluez.ProfileManager1")
    profile = Profile(bus, logger)
    registered = False
    loop = GLib.MainLoop()
    def stop(*_):
        loop.quit()
    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)
    try:
        manager.RegisterProfile(PATH, UUID, {
            "Name": "M30 LAB RFCOMM fixture", "Service": UUID, "Role": "server",
            "Channel": dbus.UInt16(22), "AutoConnect": False,
            "RequireAuthentication": True, "RequireAuthorization": False,
            "ServiceRecord": SDP_RECORD,
        })
        registered = True
        logger.event("registered", uuid=UUID, channel=22)
        props.Set("org.bluez.Adapter1", "PairableTimeout", dbus.UInt32(min(args.max_seconds, 180)))
        props.Set("org.bluez.Adapter1", "DiscoverableTimeout", dbus.UInt32(min(args.max_seconds, 180)))
        props.Set("org.bluez.Adapter1", "Pairable", dbus.Boolean(True))
        props.Set("org.bluez.Adapter1", "Discoverable", dbus.Boolean(True))
        logger.event("discoverable", deadline_seconds=args.max_seconds)
        logger.event("fixture_ready", uuid=UUID, channel=22)
        GLib.timeout_add_seconds(args.max_seconds, stop)
        loop.run()
    finally:
        profile.close_all()
        if registered:
            try: manager.UnregisterProfile(PATH)
            except dbus.DBusException: pass
        for key in ("Discoverable", "Pairable", "DiscoverableTimeout", "PairableTimeout"):
            try: props.Set("org.bluez.Adapter1", key, initial[key])
            except dbus.DBusException: pass
        logger.event("stopped_and_restored")


if __name__ == "__main__":
    main()
