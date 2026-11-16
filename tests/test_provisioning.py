"""Headless regressions for credential privacy and bounded BLE requests."""
import asyncio
import contextlib
import io
import json
import unittest
from unittest.mock import patch

import ble


class ProvisioningStub:
    def __init__(self):
        self.statuses = []

    def set_status(self, status):
        self.statuses.append(status)

    def worker(self, ssid, psk):
        pass


class CredentialHandlingTests(unittest.TestCase):
    def setUp(self):
        self.provisioning = ProvisioningStub()
        self.characteristic = ble.WifiCredCharacteristic(self.provisioning)

    def write(self, payload, options=None):
        output = io.StringIO()
        with contextlib.redirect_stdout(output), patch('ble.threading.Thread') as thread:
            self.characteristic.WriteValue(payload, options or {})
        return output.getvalue(), thread

    def test_credentials_reach_worker_without_being_logged(self):
        ssid, password = 'private-lab-network', ' sample-password-with-spaces '
        payload = json.dumps({'ssid': ssid, 'psk': password}).encode()
        output, thread = self.write(payload)
        self.assertEqual(thread.call_args.kwargs['args'], (ssid, password))
        self.assertNotIn(password, output)
        self.assertNotIn(ssid, output)
        self.assertNotIn(payload.hex(), output)
        self.assertEqual(self.characteristic._rxbuf, bytearray())
        self.assertEqual(self.characteristic._value, bytearray())
        self.assertEqual(self.provisioning.statuses, ['RECEIVED'])

    def test_malformed_json_does_not_leak_password(self):
        marker = 'private-password-marker'
        for payload in [b'{"psk":"' + marker.encode(), b'{"psk":"' + marker.encode() + b'\xff"}']:
            with self.subTest(payload=payload):
                output, thread = self.write(payload)
                self.assertNotIn(marker, output)
                self.assertFalse(thread.called)
                self.assertEqual(self.characteristic._rxbuf, bytearray())
                self.assertEqual(self.provisioning.statuses[-1], 'FAILED_BAD_JSON')

    def test_oversized_write_is_rejected_before_buffer_allocation(self):
        output, thread = self.write(b'x' * 4097)
        self.assertEqual(self.provisioning.statuses, ['FAILED_TOO_LARGE'])
        self.assertFalse(thread.called)
        self.assertEqual(self.characteristic._rxbuf, bytearray())

    def test_large_or_negative_offset_is_rejected(self):
        for offset in (65535, -1):
            with self.subTest(offset=offset):
                _, thread = self.write(b'x', {'offset': type('Offset', (), {'value': offset})()})
                self.assertEqual(self.provisioning.statuses[-1], 'FAILED_TOO_LARGE')
                self.assertFalse(thread.called)
                self.assertEqual(self.characteristic._rxbuf, bytearray())

    def test_invalid_field_types_are_rejected(self):
        for data in ({'ssid': 123, 'psk': 'abc'}, {'ssid': 'x' * 33, 'psk': 'abc'}, []):
            with self.subTest(data=data):
                _, thread = self.write(json.dumps(data).encode())
                self.assertEqual(self.provisioning.statuses[-1], 'FAILED_BAD_FIELDS')
                self.assertFalse(thread.called)

    def test_ble_is_disabled_without_explicit_opt_in(self):
        with patch.dict('os.environ', {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, 'BLE provisioning is disabled'):
                asyncio.run(ble.main())


if __name__ == '__main__':
    unittest.main()
