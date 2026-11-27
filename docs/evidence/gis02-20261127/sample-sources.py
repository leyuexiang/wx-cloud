"""有界只读抽检已登记来源的每个可用层级，不下载或扫描全量对象。"""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote, unquote, urlencode, urlsplit
import gzip, hashlib, io, json, math, struct, time, xml.etree.ElementTree as ET
import requests
from PIL import Image

B = Path(__file__).resolve().parent
OUTPUT = B / 'sampling'
OUTPUT.mkdir(exist_ok=True)
CATALOG = B / 'evidence/verify-formal-web-origin.json'
CERT = B.parent / 'shared-gis-weather-20261127/server.crt'
LIMIT, DECODED_LIMIT = 8 * 1024 * 1024, 16 * 1024 * 1024
sessions = __import__('threading').local()

def fetch(url, terrain=False):
    """连接复用但不共享线程状态，禁止代理环境、重定向及凭据透传。"""
    if not hasattr(sessions, 'http'):
        sessions.http = requests.Session()
        sessions.http.trust_env = False
    started = time.monotonic()
    headers = {'Accept-Encoding': 'identity'}
    if terrain: headers['Accept'] = 'application/vnd.quantized-mesh,application/octet-stream;q=0.9'
    with sessions.http.get(url, headers=headers, timeout=(3, 8), verify=str(CERT),
                           allow_redirects=False, stream=True) as response:
        data = response.raw.read(LIMIT + 1, decode_content=False)
        assert len(data) <= LIMIT, '响应超过 8 MiB'
        result = {'status': response.status_code, 'bytes': len(data),
                  'sha256': hashlib.sha256(data).hexdigest(), 'elapsedMs': round((time.monotonic()-started)*1000)}
        return result, data

def terrain_info(body):
    """按公开量化网格规范校验结构和索引，不把格式校验称为高程精度验收。"""
    compressed = body.startswith(b'\x1f\x8b')
    if compressed:
        with gzip.GzipFile(fileobj=io.BytesIO(body)) as stream: body = stream.read(DECODED_LIMIT + 1)
    assert 92 <= len(body) <= DECODED_LIMIT, '地形解码长度异常'
    header = struct.unpack_from('<3d2f7d', body)
    assert all(math.isfinite(value) for value in header[:9]) and header[3] <= header[4] and header[8] >= 0, '几何头异常'
    # 遮挡点与几何分开记录；允许继续检查几何不表示接受非有限元数据。
    horizon_finite = all(math.isfinite(value) for value in header[9:])
    count = struct.unpack_from('<I', body, 88)[0]
    assert 3 <= count <= (len(body)-92)//6, '顶点数量越界'
    offset = 92
    for axis in range(3):
        value = 0
        for (encoded,) in struct.iter_unpack('<H', body[offset:offset + count*2]):
            value += (encoded >> 1) ^ -(encoded & 1)
            assert 0 <= value <= 32767, '量化顶点超出范围'
        offset += count*2
    size = 4 if count > 65536 else 2
    offset = (offset + size - 1) // size * size
    triangles = struct.unpack_from('<I', body, offset)[0]
    offset += 4
    end = offset + triangles*3*size
    assert triangles > 0 and end <= len(body), '三角形数据越界'
    highest = 0
    code = '<I' if size == 4 else '<H'
    for (encoded,) in struct.iter_unpack(code, body[offset:end]):
        assert 0 <= highest - encoded < count, '三角形索引越界'
        if encoded == 0: highest += 1
    offset = end
    for edge in range(4):
        length = struct.unpack_from('<I', body, offset)[0]
        offset += 4
        end = offset + length*size
        assert end <= len(body), '边界索引长度越界'
        assert all(index[0] < count for index in struct.iter_unpack(code, body[offset:end])), '边界索引越界'
        offset = end
    extensions = []
    while offset < len(body):
        extension, length = struct.unpack_from('<BI', body, offset)
        offset += 5
        assert offset + length <= len(body), '扩展长度越界'
        extensions.append(extension)
        offset += length
    return {'format': 'quantized-mesh-1.0', 'gzip': compressed, 'vertices': count,
            'triangles': triangles, 'minimumHeight': header[3], 'maximumHeight': header[4], 'extensions': extensions,
            'horizonPointFinite': horizon_finite,
            'horizonPoint': [value if math.isfinite(value) else str(value) for value in header[9:]]}

def inventory(prefix):
    # 每层只取至多四条实际对象，选首尾两个；这不代表空间均匀或完整枚举。
    query = urlencode({'list-type': '2', 'prefix': prefix, 'max-keys': '4'})
    result, data = fetch('http://192.168.10.120:9000/9tyf?' + query)
    assert result['status'] == 200, '既有对象目录不可读取'
    tree = ET.fromstring(data)
    keys = [node.findtext('{*}Key') for node in tree.findall('{*}Contents')]
    keys = [key for key in keys if key and key.endswith(('.jpg', '.jpeg', '.png', '.terrain'))]
    assert keys, '已登记层级没有可用样本对象'
    return list(dict.fromkeys([keys[0], keys[-1]]))

def samples(item):
    public = item['path'].rstrip('/')
    path = urlsplit(public).path
    jobs = []
    if '/terrain-dem/' in path:
        meta, body = fetch('http://192.168.10.120:58000/gis-images/terrain/layer.json')
        assert meta['status'] == 200
        metadata = json.loads(body)
        for level in range(item['level'] + 1):
            ranges = metadata['available'][level]
            assert ranges, '描述中的层级没有可用范围'
            points = [(ranges[0]['startX'], ranges[0]['startY']), (ranges[-1]['endX'], ranges[-1]['endY'])]
            for x, y in dict.fromkeys(points):
                suffix = f'{level}/{x}/{y}.terrain'
                jobs.append({'id': item['id'], 'name': item['gisName'], 'level': level,
                    'public': public.rsplit('/', 1)[0] + '/' + suffix,
                    'source': 'http://192.168.10.120:58000/gis-images/terrain/' + suffix, 'terrain': True})
    else:
        relative = unquote(path.removeprefix('/gis-source/'))
        if item['dataType'] == 'ELEVATION': relative = relative.rsplit('/', 1)[0]
        for level in range(item['level'] + 1):
            for key in inventory(relative.rstrip('/') + '/' + str(level) + '/'):
                encoded = quote(key, safe='/')
                jobs.append({'id': item['id'], 'name': item['gisName'], 'level': level,
                    'public': 'https://192.168.10.148:9447/gis-source/' + encoded,
                    'source': 'http://192.168.10.110:58000/gis-source/' + encoded,
                    'storage': 'http://192.168.10.120:9000/9tyf/' + encoded,
                    'terrain': item['dataType'] == 'ELEVATION'})
    return jobs

def probe(job):
    result = {key: value for key, value in job.items() if key != 'terrain'}
    try:
        proxy, body = fetch(job['public'], job['terrain'])
        source, original = fetch(job['source'], job['terrain'])
        result.update(proxy=proxy, sourceResponse=source)
        assert proxy['status'] == source['status'] == 200, '读取未返回 200'
        assert body == original, '代理与源站字节不一致'
        if 'storage' in job:
            storage, stored = fetch(job['storage'], job['terrain'])
            result['storageResponse'] = storage
            assert storage['status'] == 200 and stored == original, '既有 MinIO 与服务字节不一致'
        result['transportPassed'] = True
        if job['terrain']: result['decoded'] = terrain_info(body)
        else:
            with Image.open(io.BytesIO(body)) as image:
                image.load()
                assert image.size == (256, 256) and image.format in ('JPEG','PNG'), '影像格式或尺寸不符'
                result['decoded'] = {'format': image.format, 'width': image.width, 'height': image.height}
        result['structurePassed'] = True
        result['passed'] = result['decoded'].get('horizonPointFinite', True)
        if not result['passed']: result['reason'] = '源站地形遮挡点包含非有限数值，未据几何可解码认定元数据有效'
    except Exception as error:
        result.update(passed=False, errorType=type(error).__name__, reason=str(error).split('?')[0][:240])
    return result

if __name__ == '__main__':
    catalog = json.loads(CATALOG.read_text(encoding='utf-8'))['catalogs']
    items = catalog['tenant-SATELLITE'] + catalog['tenant-ELEVATION']
    assert len(items) == 5
    jobs = []
    for item in items:
        found = samples(item)
        jobs.extend(found)
        print(json.dumps({'source': item['gisName'], 'levels': item['level']+1, 'samples': len(found)}, ensure_ascii=True), flush=True)
    assert len(jobs) <= 122
    with ThreadPoolExecutor(max_workers=2) as pool: results = list(pool.map(probe, jobs))
    report = {'timestampUtc': datetime.now(timezone.utc).isoformat(), 'readOnly': True, 'concurrency': 2,
        'strategy': '每层最多两条既有对象样本；DEM90m按已声明可用范围首尾节点。非空间均匀或全量抽检。',
        'specification': 'https://github.com/CesiumGS/quantized-mesh',
        'scriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'samples': len(results), 'passed': sum(row['passed'] for row in results),
        'transportPassed': sum(row.get('transportPassed', False) for row in results),
        'structurePassed': sum(row.get('structurePassed', False) for row in results), 'results': results}
    (OUTPUT / 'sample-results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({key: report[key] for key in ('samples','passed','transportPassed','structurePassed','readOnly')}, ensure_ascii=True))
    raise SystemExit(0 if report['passed'] == report['samples'] else 1)
