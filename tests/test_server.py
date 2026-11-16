"""Exercise the real Flask handlers with a headless display double."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch


class HttpPrivacyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.stack = contextlib.ExitStack()
        cls.folder = cls.stack.enter_context(tempfile.TemporaryDirectory())
        cls.stack.enter_context(patch.dict(os.environ, {'DISPLAYDOORSILL_DATA_DIR': cls.folder}))
        cls.stack.enter_context(patch.dict(sys.modules, {'tkinter': MagicMock()}))
        cls.stack.enter_context(patch('PIL.ImageTk.PhotoImage'))
        spec = importlib.util.spec_from_file_location('displaydoorsill_test_server', Path(__file__).resolve().parents[1] / 'server.py')
        cls.server = importlib.util.module_from_spec(spec)
        with contextlib.redirect_stdout(io.StringIO()):
            spec.loader.exec_module(cls.server)
        cls.client = cls.server.app.test_client()

    @classmethod
    def tearDownClass(cls):
        cls.stack.close()

    def setUp(self):
        self.server.root.event_generate.reset_mock()

    def test_http_defaults_to_loopback(self):
        self.assertEqual(self.server.HOST, os.environ.get('DISPLAYDOORSILL_HOST', '127.0.0.1'))

    def test_text_reaches_display_without_logging_content(self):
        marker = 'private-display-content-marker'
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            response = self.client.post('/send-text', json={'text': marker})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(self.server.incoming_message, marker)
        self.assertNotIn(marker, output.getvalue())
        self.server.root.event_generate.assert_called_once()

    def test_large_request_returns_413_without_display_update(self):
        response = self.client.post('/send-text', data=b'x' * (10 * 1024 * 1024 + 1))
        self.assertEqual(response.status_code, 413)
        self.server.root.event_generate.assert_not_called()

    def test_invalid_text_is_rejected(self):
        for value in ([], {}, None, 'x' * 4097):
            with self.subTest(value_type=type(value).__name__):
                response = self.client.post('/send-text', json={'text': value})
                self.assertEqual(response.status_code, 400)
                self.server.root.event_generate.assert_not_called()


if __name__ == '__main__':
    unittest.main()
