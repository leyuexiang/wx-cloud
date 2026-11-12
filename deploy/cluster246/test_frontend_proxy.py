"""只切新管理端IoT代理，不能改写Unity正在使用的12525或旧站点API。"""
import unittest
from frontend_proxy import candidate_config


class FrontendProxyTest(unittest.TestCase):
    def test_specific_iot_proxy_precedes_general_api_and_preserves_other_text(self):
        before = '''server {
    location /threeWeb/ { try_files $uri /threeWeb/index.html; }
    location ~ "^/dts-api/admin-api/(?:system|iot|dim3d)/.*$" {
        proxy_pass http://127.0.0.1:12525;
    }
    location /old-api/ { proxy_pass http://127.0.0.1:12522; }
}
'''
        result = candidate_config(before)
        self.assertIn('proxy_pass http://127.0.0.1:48097;', result)
        self.assertLess(result.index('IOT-DIST-20261111'), result.index('(?:system|iot|dim3d)'))
        # 去除新增区块后必须逐字等于原配置，避免影响旧页面和非IoT接口。
        start = result.index('    # IOT-DIST-20261111')
        end = result.index('    # END-IOT-DIST-20261111\n', start) + len('    # END-IOT-DIST-20261111\n')
        self.assertEqual(result[:start] + result[end:], before)
        self.assertIn('proxy_set_header x-bot-user "";', result)

    def test_unknown_baseline_or_duplicate_application_stops(self):
        for text in ['server {}', '# IOT-DIST-20261111\n    location ~ "^/dts-api/admin-api/x" {}']:
            with self.assertRaises(ValueError): candidate_config(text)


if __name__ == '__main__': unittest.main()
