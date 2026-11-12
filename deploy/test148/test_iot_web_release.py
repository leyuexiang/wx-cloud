"""静态发布边界回归：保护旧站点、隔离代理及回退后的有效证书。"""
import unittest
from iot_web_release import render_config, rollback_config


BEFORE = '''server {
    listen 192.168.10.148:9447 ssl;
    ssl_certificate /old/server.crt;
    ssl_certificate_key /old/server.key;
    root /opt/wx/editor-web148/site;
    location = / { return 302 /threeWeb/; }
    location /threeWeb/ { try_files $uri /threeWeb/index.html; }
    location /old-api/ { proxy_pass http://127.0.0.1:12522; }
}
'''
SITE = '/home/wx-editor-web148/releases/iot-web-20261111/site'
CERT = '/home/wx-editor-web148/releases/iot-web-20261111/server.crt'


class IotWebReleaseTest(unittest.TestCase):
    def test_old_routes_are_preserved(self):
        candidate = render_config(BEFORE, SITE, CERT)
        self.assertIn('location /threeWeb/ { try_files $uri /threeWeb/index.html; }', candidate)
        self.assertIn('location /old-api/ { proxy_pass http://127.0.0.1:12522; }', candidate)
        self.assertIn('location = / { return 302 /threeWeb/; }', candidate)
        self.assertIn('proxy_pass http://127.0.0.1:12525;', candidate)

    def test_new_api_prefix_is_whitelisted_and_stripped(self):
        result = render_config(BEFORE, SITE, CERT)
        self.assertIn('rewrite ^/dts-api(/.*)$ $1 break;', result)
        self.assertIn('location /dts-api/ { return 404; }', result)
        self.assertIn('proxy_set_header x-bot-user "";', result)
        self.assertNotIn('iot/**', result)
        self.assertNotIn('telemetry', result)

    def test_hash_assets_never_fall_back_to_html(self):
        result = render_config(BEFORE, SITE, CERT)
        self.assertIn('location ^~ /dts/assets/', result)
        self.assertIn('try_files $uri =404;', result)
        self.assertIn('try_files $uri $uri/ /dts/index.html;', result)
        self.assertIn('public, max-age=31536000, immutable', result)

    def test_rollback_keeps_new_certificate_but_removes_new_routes(self):
        rollback = rollback_config(BEFORE, CERT)
        self.assertIn('ssl_certificate ' + CERT + ';', rollback)
        self.assertIn('ssl_certificate_key /old/server.key;', rollback)
        self.assertNotIn('/dts/', rollback)

    def test_wrong_directory_duplicate_scope_and_missing_anchor_are_rejected(self):
        for config, directory in [(BEFORE, '/tmp/site'), ('server {}', SITE),
                                  (render_config(BEFORE, SITE, CERT), SITE)]:
            with self.assertRaises(ValueError):
                render_config(config, directory, CERT)


if __name__ == '__main__':
    unittest.main()
