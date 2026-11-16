import asyncio		# event loop für asynchrones dbus
import json			
import socket		# to find local IP
import subprocess	# calls nmcli
import threading	# wifi connect logic runs on a thread
import time
import os

# D-Bus: Inter Process Communication in Linux
# Programms can speak to one another even though: different languages, different processes, they run as system service
# ble.py communicates with blueZ (Linux Bluetooth Daemon) via this Bus
# Here the system bus is used  (bus_type=BusType.SYSTEM) to communicate with hardware and system services
# "s" -> string, "b" -> bool, "ay" -> byte array, "ao" -> object paths
from dbus_next.aio import MessageBus
from dbus_next.service import ServiceInterface, method, dbus_property, signal
from dbus_next import Variant
from dbus_next.constants import BusType
from dbus_next.service import PropertyAccess
from dbus_next.errors import DBusError

# ========== BLE UUIDs ==========
SERVICE_UUID   = "12345678-1234-5678-1234-56789abcdef0"
WIFI_CRED_UUID = "12345678-1234-5678-1234-56789abcdef1"
STATUS_UUID    = "12345678-1234-5678-1234-56789abcdef2"
RESULT_UUID    = "12345678-1234-5678-1234-56789abcdef3"

DEVICE_NAME = "MBDisplay-Setup"
HTTP_PORT = 5000

IFNAME = "wlan0"
CON_NAME = "CAR_WIFI"

BLUEZ_SERVICE = "org.bluez"
GATT_MANAGER_IFACE = "org.bluez.GattManager1"
LE_ADV_MANAGER_IFACE = "org.bluez.LEAdvertisingManager1"

ROOT_PATH = "/com/mbdisplay"
APP_PATH = ROOT_PATH
SVC_PATH = f"{ROOT_PATH}/service0"
CHAR_WIFI_PATH = f"{SVC_PATH}/char_wifi"
CHAR_STATUS_PATH = f"{SVC_PATH}/char_status"
CHAR_RESULT_PATH = f"{SVC_PATH}/char_result"

ADV_PATH = f"{ROOT_PATH}/adv0"




# ========== Helpers ==========
# subprocess.run(...): starts process and blocks until finished. Thats why threads are needed
# stdout is collected, stderr into stdout, text=True: strings instead of bytes
# returns the return code and stdout
def run(cmd: list[str]) -> tuple[int, str]: # list of strings: ["nmcli", "connection", "up", "CAR-WIFI"], return value (tuple) int as exit code and str as output
    # subprocess is a python module to start os programms
    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True) # stdout = subprocess.PIPE : send output to python instead of terminal
    return p.returncode, (p.stdout or "")


def get_local_ip() -> str:
    # creates a socket object, AF_INET: Adress Family IPv4 192.168.x.x, SOCK_DGRAM: Datagram, UDP
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM) # UPD instead of TCP, no real connection?
    try:
        # 8.8.8.8 is a public IP, always reachable and forces default route
        s.connect(("8.8.8.8", 80)) # bei UDP: Kernel decides: which interface (wlan0), which local IP, which Route.
        # returns local IP, local Port
        return s.getsockname()[0]
    except Exception:
        return "0.0.0.0" # no valid adress
    finally:
        s.close()


def get_current_ssid() -> str:
    """Returns the SSID the Pi is currently connected to, or empty string if not connected."""
    rc, out = run(["nmcli", "-t", "-f", "active,ssid", "dev", "wifi"])
    # Output lines look like: "yes:MyNetwork" or "no:OtherNetwork"
    if rc != 0:
        return ""
    for line in out.strip().split("\n"):
        line = line.strip()
        if line.startswith("yes:"):
            return line[4:]  # everything after "yes:"
    return ""


# WIFI on, delete old connection, new SSID, set key_mgmt, set new PSK, activate connection
# OS tries: Scan for SSID, Authenticate, Associate, get IP, set Route
def nmcli_profile_connect(ssid: str, psk: str, key_mgmt: str = "wpa-psk") -> tuple[bool, str]:
    run(["nmcli", "radio", "wifi", "on"]) # WIFI on

    # Delete old profile — may need a moment for NM to release it
    run(["nmcli", "connection", "delete", CON_NAME])  # ignore errors, if a profile does not exist: unknown connection
    time.sleep(0.5)  # give NetworkManager time to clean up

    # Try adding new profile with retry (NM can be slow to release)
    rc, out = None, ""
    for attempt in range(3):
        rc, out = run(["nmcli", "connection", "add",
                       "type", "wifi", "ifname", IFNAME,
                       "con-name", CON_NAME, "ssid", ssid])
        if rc == 0:
            break
        print(f"[WIFI] Add attempt {attempt+1} failed: {out.strip()}")
        time.sleep(1.0)  # wait before retry

    if rc != 0:
        return False, "ADD_FAILED"

    rc, out = run(["nmcli", "connection", "modify", CON_NAME,
                   "802-11-wireless-security.key-mgmt", key_mgmt]) # set key management
    if rc != 0:
        return False, "KEYMGMT_FAILED"

    rc, out = run(["nmcli", "connection", "modify", CON_NAME,
                   "802-11-wireless-security.psk", psk]) # set password
    if rc != 0:
        return False, "PSK_FAILED"

    rc, out = run(["nmcli", "connection", "up", CON_NAME]) # activate connection
    if rc != 0:
        # copy out in low and set characters to low, so that we can search without having to care about that
        low = out.lower()
        # NM returns errors as: secrets were required, incorrect password, failed auth
        if "secrets" in low or "incorrect" in low or "auth" in low:
            return False, "AUTH"
        return False, "UP_FAILED"

    return True, "OK"

# ========== DBus Base ==========

# ======= ADVERTISMENT ==========
class Advertisement(ServiceInterface):			# ServiceInterface means that this python object is an exportable D-Bus object
    # constructor
    def __init__(self):
        super().__init__("org.bluez.LEAdvertisement1") # super calls conctructor of the parent class ServiceInterface, org.bluez.LEAdv.. : IF Name, that bluez expects
        self.path = ADV_PATH # D-Bus Objekt path, Adress on the bus
        self._type = "peripheral" # offers GATT properties, other option "broadcast" for beacons
        self._service_uuids = [SERVICE_UUID] # show that we can scan only for devices with this service uuid
        self._local_name = DEVICE_NAME
    # BlueZ can Read the property, not set it
    @dbus_property(access=PropertyAccess.READ)
    def Type(self) -> "s": # s for string
        return self._type 

    @dbus_property(access=PropertyAccess.READ)
    def ServiceUUIDs(self) -> "as": # as for array of strings, returns a list of strings
        return self._service_uuids

    @dbus_property(access=PropertyAccess.READ)
    def LocalName(self) -> "s":
        return self._local_name
    
    # blueZ can call this method
    @method()
    def Release(self):
        print("[BLE] Advertisement released")




    
# ========== GATT ===========
# D-Bus Object, that implements the service "org.bluez.GattService1"
# BlueZ uses this object to know: which service uuid we offer, if the service is primary and which characteristics are included
# Then BlueZ Builds a Real GATT-Service
class GattService(ServiceInterface):
    def __init__(self):
        super().__init__("org.bluez.GattService1") 
        self.path = SVC_PATH # D-Bus Object path
        self._uuid = SERVICE_UUID # BLE Service UUID
        self._primary = True
        self.chars = [] # List of characteristics included

    @dbus_property(access=PropertyAccess.READ)
    def UUID(self) -> "s":
        return self._uuid

    @dbus_property(access=PropertyAccess.READ)
    def Primary(self) -> "b": # D-Bus boolean
        return self._primary # Primary is true

    @dbus_property(access=PropertyAccess.READ)
    def Characteristics(self) -> "ao": # D-Bus Array of object paths
        return [c.path for c in self.chars] # returns paths of characteristics: char_wifi, char_status, char_result
    
    # Signal to notify on a value change
    def _props_changed(self, iface: str, changed: dict):
        self.PropertiesChanged(iface, changed, [])
    
    # D-Bus Signal
    @signal()#                                        
    def PropertiesChanged(self, interface: "s", changed: "a{sv}", invalidated: "as"):
        pass

# A Characteristic is a Datapoint inside of a service
# BlueZ reads this object to build a real GATT-Characteristic
class GattCharacteristic(ServiceInterface):
    def __init__(self, path: str, uuid: str, flags: list[str], service_path: str):
        super().__init__("org.bluez.GattCharacteristic1")
        self.path = path
        self._uuid = uuid # BLE UUID of this specific characteristic
        self._flags = flags # list of abilities, read, write, notify
        self._service_path = service_path
        self._value = bytearray()
        self._notifying = False
        self._bus = None
    
    def set_bus(self, bus):
        self._bus = bus
    
    @dbus_property(access=PropertyAccess.READ)
    def UUID(self) -> "s":
        return self._uuid

    @dbus_property(access=PropertyAccess.READ)
    def Service(self) -> "o":
        return self._service_path

    @dbus_property(access=PropertyAccess.READ)
    def Flags(self) -> "as":
        return self._flags

    @dbus_property(access=PropertyAccess.READ)
    def Value(self) -> "ay":
        return bytes(self._value) # from bytearray -> byt object (not mutable)

    @method()
    def ReadValue(self, options: "a{sv}") -> "ay":
        return bytes(self._value)

    @method()
    def WriteValue(self, value: "ay", options: "a{sv}"):
        print("[BLE] WriteValue called on ", self.path, "len= ", len(value), "options=", options)
        self._value = bytearray(value)

    @method()
    def StartNotify(self):
        print(f"[BLE] StartNotify on {self.path} - setting _notifying=true")
        self._notifying = True

    @method()
    def StopNotify(self):
        self._notifying = False
        
    def _props_changed(self, iface: str, changed: dict):
        print(f"[DBG] _props_changed called: interface={iface}")
        self.PropertiesChanged(iface, changed, [])
        
    @signal()
    def PropertiesChanged(self, interface: "s", changed: "a{sv}", invalidated: "as"):
        print(f"[DBG] PropertiesChanged emitted: interface={interface}")
    
    
    def notify(self, data: bytes):
        print(f"[DBG] Notify called with {len(data)} bytes")
       # if not self._notifying:
        #    print("[DBG] Not notifying - returning early")
         #   return
        self._value = bytearray(data)
        if self._bus is None:
            print("[ERROR] Bus not set")
            return
        from dbus_next import Message, MessageType, Variant
        
        msg = Message(
            message_type = MessageType.SIGNAL,
            path=self.path,
            interface="org.freedesktop.DBus.Properties",
            member="PropertiesChanged",
            signature="sa{sv}as",
            body=[
                "org.bluez.GattCharacteristic1",
                {"Value": Variant("ay", bytes(self._value))},
                []
            ]
        )
        print(f"[DBG] Sending signal on path={self.path}")
        self._bus.send(msg)
        print(f"[DBG] Signal sent")

        
#========= Application ==========
class Application(ServiceInterface):
    def __init__(self, service: GattService):
        super().__init__("org.freedesktop.DBus.ObjectManager") # IF ObjectManager, Standard IF - not BlueZ specific
        self.path = APP_PATH
        self.service = service
    # BlueZ must find the complete GATT Tree (Services, Characteristics, Properties)
    # So the managed objects get passed to it via org.freedesktop.BBus.ObjectManager.GetManagedObjects
    @method()
    def GetManagedObjects(self) -> "a{oa{sa{sv}}}":
        managed = {} # Return Dict
        
        svc = self.service
        managed[svc.path] = {
            "org.bluez.GattService1": {
                "UUID": Variant("s", svc._uuid),
                "Primary": Variant("b", svc._primary),
                "Characteristics": Variant("ao", [c.path for c in svc.chars]),
            }
        }
        for c in svc.chars:
            managed[c.path] = {
                "org.bluez.GattCharacteristic1": {
                    "UUID": Variant("s", c._uuid),
                    "Service": Variant("o", svc.path),
                    "Flags": Variant("as", c._flags),
                    "Value": Variant("ay", bytes(c._value)),
                }
            }
            
        return managed

# ========== Provisioning Logic wiring ==========
# Logs Status, connects wifi, gets ip, send ip/port to app
# Communicates with GattCharacterisic, nmcli_profile_connect and get_local_ip
class Provisioning:
    def __init__(self, loop: asyncio.AbstractEventLoop, status_char: GattCharacteristic, result_char: GattCharacteristic):
        self.loop = loop
        self.status_char = status_char
        self.result_char = result_char

    def set_status(self, s: str):
        print("[BLE][STATUS]", s)
        data = s.encode("utf-8")
        self.loop.call_soon_threadsafe(self.status_char.notify, data)
    
    def set_result(self, obj: dict):
        payload = json.dumps(obj).encode("utf-8") # json.dumps: dict to json string, then encoded in bytes
        print("[BLE][RESULT]", payload.decode("utf-8")) # prints json in terminal
        self.loop.call_soon_threadsafe(self.result_char.notify, payload)# notify Method called 
    
    # starts in a thread (in WifiCredCharacteristic.WriteValue), so that BLE/asyncio does not block
    def worker(self, ssid: str, psk: str):
        print("[WIFI] Provisioning started")

        # ===== Check if already connected to the same SSID =====
        current_ssid = get_current_ssid()
        if current_ssid == ssid:
            ip = get_local_ip()
            if ip != "0.0.0.0":
                print("[BLE] Already connected — skipping reconnect")
                self.set_status("ALREADY_CONNECTED")
                self.set_result({"ip": ip, "port": HTTP_PORT})
                return
            else:
                print("[BLE] Connected but no valid IP — will reconnect")

        self.set_status("CONNECTING")

        # WPA2 then WPA3 fallback
        for km in ["wpa-psk", "sae"]:
            print(f"WORKER TRYING KEY MGMT WITH {km}")
            ok, reason = nmcli_profile_connect(ssid, psk, km) #nmcli_profile_connect is blocking, thread used -> OK
            print(f"WORKER RESULT: ok = {ok}, reason = {reason}")
            if ok:
                time.sleep(2.0) # blocks for 2s, so that DHCP has time to finish. nmcli connection up can return ok before DHCP is finished
                ip = get_local_ip() # Helper Function
                if ip == "0.0.0.0":
                    self.set_status("FAILED_NO_IP")
                    return
                self.set_status("CONNECTED")
                self.set_result({"ip": ip, "port": HTTP_PORT})
                return
            if reason == "AUTH":
                self.set_status("FAILED_AUTH")
                return

        self.set_status("FAILED_OTHER")


# Special GATT Characteristic
# App can [write], to send WIFI credentials
# Inherits from GattCharacteristic -> is a D-Bus object
# Communicates with provisioning (status and thread)
class WifiCredCharacteristic(GattCharacteristic):
    def __init__(self, prov: Provisioning): # constructor get a provisioning object
        super().__init__(CHAR_WIFI_PATH, WIFI_CRED_UUID, ["write"], SVC_PATH) # contructor from GattCharacteristic called, path, uuid, flags, service_path setted
        self.prov = prov # reference
        self._rxbuf = bytearray()
        self._max_buf = 4096
        
    # BlueZ calls this method if a client writes
    @method() #              Bytearray         Metadaten
    def WriteValue(self, value: "ay", options: "a{sv}"):
        """
        Robust BLE JSON receiver.
        - Works with single-write JSON (your current case: len=32, mtu=517)
        - Also works if BlueZ delivers offset-based long writes in the future
        - Rejects malformed payloads without logging credential data
        - Removes NUL padding bytes
        """
        try:
            chunk = bytes(value)

            # --- Optional: offset-aware assembly (doesn't hurt even if offset isn't used) ---
            off_v = options.get("offset") if isinstance(options, dict) else None
            offset = int(off_v.value) if off_v is not None else 0

            if offset < 0 or offset + len(chunk) > self._max_buf:
                self._rxbuf.clear()
                self.prov.set_status("FAILED_TOO_LARGE")
                return

            # Ensure buffer exists
            if not hasattr(self, "_rxbuf"):
                self._rxbuf = bytearray()
    
            if offset == 0:
                # new message begins
                self._rxbuf = bytearray()

            # place chunk at correct offset
            if len(self._rxbuf) < offset:
                self._rxbuf.extend(b"\x00" * (offset - len(self._rxbuf)))
            end = offset + len(chunk)
            if len(self._rxbuf) < end:
                self._rxbuf.extend(b"\x00" * (end - len(self._rxbuf)))
            self._rxbuf[offset:end] = chunk

            # --- Decode + parse JSON ---
            raw_bytes = bytes(self._rxbuf).replace(b"\x00", b"")
            raw_str = raw_bytes.decode("utf-8", errors="strict").strip()

            # Never log credential bytes, decoded JSON, or parser exceptions.
            data = json.loads(raw_str)
            self._rxbuf.clear()
            ssid = data.get("ssid") if isinstance(data, dict) else None
            psk = data.get("psk") if isinstance(data, dict) else None
            if not isinstance(ssid, str) or not isinstance(psk, str):
                self.prov.set_status("FAILED_BAD_FIELDS")
                return
            if not ssid or len(ssid.encode("utf-8")) > 32 or not psk:
                self.prov.set_status("FAILED_BAD_FIELDS")
                return

            # --- Kick off provisioning ---
            self.prov.set_status("RECEIVED")
            threading.Thread(target=self.prov.worker, args=(ssid, psk), daemon=True).start()

        except UnicodeDecodeError as e:
            # UTF-8 split / invalid bytes
            self._rxbuf.clear()
            print("[BLE][WIFI] Invalid UTF-8 payload")
            # If using offset writes, waiting for more chunks may help; otherwise fail
            self.prov.set_status("FAILED_BAD_JSON")

        except json.JSONDecodeError as e:
            # JSON still incomplete OR malformed
            self._rxbuf.clear()
            print("[BLE][WIFI] Invalid JSON payload")
            self.prov.set_status("FAILED_BAD_JSON")

        except Exception as e:
            print("[BLE][WIFI] Provisioning request failed")
            # Reset buffer to avoid getting stuck
            if hasattr(self, "_rxbuf"):
                self._rxbuf = bytearray()
            self.prov.set_status("FAILED_OTHER")


# Main is async because all D-Bus calls are asynchron
async def main():
    if os.environ.get("DISPLAYDOORSILL_ENABLE_BLE_PROVISIONING") != "1":
        raise RuntimeError(
            "BLE provisioning is disabled. Read SECURITY.md before explicitly enabling it."
        )
    # open connection to D-Bus, BlueZ runs there
    bus = await MessageBus(bus_type = BusType.SYSTEM).connect() # await means: start operation, give control to the event loop, if result continue

    # Find adapter path (usually /org/bluez/hci0)

    adapter_candidates = ["/org/bluez/hci0", "/org/bluez/hci1"]
    adapter_path = None

    for cand in adapter_candidates:
        try:
            # introspect: which IFs, methods, properties dooes this object have?
            # if it works adapter_path = cand and break, if not exception and repeat, if it does not work again no BLE adapter found
            await bus.introspect(BLUEZ_SERVICE, cand)
            adapter_path = cand
            break
        except Exception:
            pass
    if not adapter_path:
        raise RuntimeError("No BLE adapter found, hci0/hci0 missing?")
    # Managers
    adapter_intro = await bus.introspect(BLUEZ_SERVICE, adapter_path)				# Save D-Bus Object Properties, IFs and methods
    adapter_obj = bus.get_proxy_object(BLUEZ_SERVICE, adapter_path, adapter_intro)  # generates a python proxy object, via which methods can be called

    gatt_mgr = adapter_obj.get_interface(GATT_MANAGER_IFACE)  # BlueZ Manager IF, can register applications (GATT Server)
    adv_mgr = adapter_obj.get_interface(LE_ADV_MANAGER_IFACE) # BlueZ Manager IF, can register advertisements
    
    # Build GATT Tree
    service = GattService() # Build Objects in RAM, python object with path & uuid
    status_char = GattCharacteristic(CHAR_STATUS_PATH, STATUS_UUID, ["notify"], SVC_PATH) #Notify only char
    result_char = GattCharacteristic(CHAR_RESULT_PATH, RESULT_UUID, ["notify"], SVC_PATH) #Notify only char
    loop = asyncio.get_running_loop()
    prov = Provisioning(loop, status_char, result_char) # class Provisioning gets a reference on both, so that it can send a notify
    wifi_char = WifiCredCharacteristic(prov) # Write only char
    service.chars = [wifi_char, status_char, result_char] # service completed with all chars
    app = Application(service) # app is the ObjectManager, returns ManagedObjects
    adv = Advertisement() # Advertising Object
    
    # Until here everything is only available for python in RAM -> export on D-Bus to make them visivle to BlueZ
    
    # Export on bus
    bus.export(service.path, service)
    for c in service.chars:
        bus.export(c.path, c)
        c.set_bus(bus)
    bus.export(app.path, app)
    bus.export(adv.path, adv)

    prov.set_status("IDLE")

    # Register with BlueZ
    try:
        await gatt_mgr.call_register_application(app.path, {}) # Register GATT App, BlueZ calls GetManagedObjects(), on the application Object, reads services, chars and properties, sets up the GATT server
    except DBusError as e:
        print("[BLE] Register Application Error: ", e)
        raise
    try:
        await adv_mgr.call_register_advertisement(adv.path, {}) # Register Advertisement, reads Properties Type, ServiceUUIDs, LocalName, starts advert. on the adaptor
    except DBusError as e:
        print("[BLE] Register Advertisment Error: ", e)
        raise
    print("[BLE] Advertising + GATT running as", DEVICE_NAME)
    
    await asyncio.get_running_loop().create_future()  # run forever

if __name__ == "__main__":
    asyncio.run(main())