"""No SQL, real credentials or network. Tests pin ordering and guardian safety using mocks."""
import hashlib
from pathlib import Path
import ssl
import sys
import unittest
from unittest.mock import Mock,patch

sys.path.insert(0,str(Path(__file__).resolve().parent))
from tls_support import PinnedContext,connection,close_window,pymysql
from watchdog import due


class TlsControls(unittest.TestCase):
    def client(self,ctx,capabilities=2048):
        client=connection(ctx,'public-synthetic-password',defer=True)
        client.server_version='8.0.46';client.server_capabilities=capabilities
        client._sock=Mock();packets=[];client.write_packet=lambda data:packets.append(data)
        return client,packets

    def test_no_tls_rejected_before_authentication(self):
        ctx=PinnedContext(None,'0'*64);client,packets=self.client(ctx,0)
        with self.assertRaises(pymysql.err.OperationalError):client._request_authentication()
        self.assertEqual(packets,[]);self.assertFalse(ctx.pin_verified)

    def check_cert_failure(self,error):
        ctx=PinnedContext(None,'0'*64);client,packets=self.client(ctx)
        with patch.object(ssl.SSLContext,'wrap_socket',side_effect=error):
            with self.assertRaises(ssl.SSLCertVerificationError):client._request_authentication()
        self.assertEqual([len(p) for p in packets],[32])  # SSLRequest only, no username or auth response.
        self.assertEqual(ctx.verify_mode,ssl.CERT_REQUIRED);self.assertFalse(ctx.pin_verified)

    def test_untrusted_ca_rejected_before_authentication(self):
        self.check_cert_failure(ssl.SSLCertVerificationError('synthetic untrusted CA'))

    def test_invalid_certificate_rejected_before_authentication(self):
        self.check_cert_failure(ssl.SSLCertVerificationError('synthetic certificate expired'))

    def test_wrong_pin_rejected_before_authentication(self):
        ctx=PinnedContext(None,'0'*64);client,packets=self.client(ctx)
        sock=Mock();sock.getpeercert.return_value=b'public-synthetic-der'
        with patch.object(ssl.SSLContext,'wrap_socket',return_value=sock):
            with self.assertRaises(ssl.SSLCertVerificationError):client._request_authentication()
        self.assertEqual([len(p) for p in packets],[32]);sock.close.assert_called_once()

    def test_expected_pin_checked_on_returned_socket(self):
        ctx=PinnedContext(None,hashlib.sha256(b'public-synthetic-der').hexdigest())
        sock=Mock();sock.getpeercert.return_value=b'public-synthetic-der';sock.version.return_value='TLSv1.3';sock.cipher.return_value=('TLS_AES_256_GCM_SHA384','TLSv1.3',256)
        with patch.object(ssl.SSLContext,'wrap_socket',return_value=sock):
            self.assertIs(ctx.wrap_socket(Mock()),sock)
        self.assertTrue(ctx.pin_verified);self.assertEqual(ctx.negotiated_protocol,'TLSv1.3')

    def test_guard_deadline_parent_exit_and_disarm(self):
        self.assertFalse(due(279,100,True,False));self.assertTrue(due(280,100,True,False))
        self.assertTrue(due(101,100,False,False));self.assertFalse(due(400,100,False,True))

    def test_close_locks_before_kill_and_verifies(self):
        commands=[]
        def fake(query):
            commands.append(query)
            if query.startswith('SELECT ID,USER'):return '123\tdimabug_qa_app'
            if 'PROCESSLIST' in query:return '0'
            if 'mysql.user' in query:return '2'
            return ''
        self.assertTrue(close_window(fake))
        self.assertIn('ACCOUNT LOCK',commands[0]);self.assertIn('KILL CONNECTION 123;',commands)

    def test_guard_never_kills_different_user(self):
        commands=[]
        def fake(query):
            commands.append(query)
            return '123\tother_user' if query.startswith('SELECT ID,USER') else ''
        with self.assertRaises(RuntimeError):close_window(fake)
        self.assertFalse(any(q.startswith('KILL') for q in commands))

    def test_guard_failed_close_is_not_success(self):
        def fake(query):
            if query.startswith('SELECT ID,USER'):return ''
            return '1' if 'PROCESSLIST' in query else ''
        with self.assertRaises(RuntimeError):close_window(fake)


if __name__=='__main__':unittest.main()
