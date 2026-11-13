'use strict';

// 仅修补已逐行核对的现场版本；上游文件变化时停止，不能把补丁套到未知转换器。
const fs = require('fs');
const crypto = require('crypto');
const hashes = {
  '/app/lib/preprocess.js': '7d1cad0253318d5d2d2c5098c58d50976e0fe59c0ebe8525e22ed62fc8602229',
  '/app/server.js': '20de72efb4e8c7958ecc2c0ae0909640c69181a72f56074828fb10ae8434e417',
};
const sources = {};
for (const [file, expected] of Object.entries(hashes)) {
  const content = fs.readFileSync(file);
  if (crypto.createHash('sha256').update(content).digest('hex') !== expected) {
    throw new Error('转换器基线不匹配：' + file);
  }
  sources[file] = content.toString('utf8');
}

function replaceOnce(source, before, after) {
  if (source.split(before).length !== 2) throw new Error('转换器补丁定位不唯一');
  return source.replace(before, after);
}

let preprocess = sources['/app/lib/preprocess.js'];
preprocess = "const { parseTextureReference } = require('./material-reference.cjs');\n" + preprocess;
preprocess = replaceOnce(preprocess,
  "const raw = m[2].replace(/\\\\/g, '/');",
  "const reference = parseTextureReference(m[2]);\n      const raw = reference.file.replace(/\\\\/g, '/');");
preprocess = replaceOnce(preprocess,
  "return rel ? m[1] + ' ' + rel : line;",
  "return rel ? m[1] + ' ' + reference.options + rel : line;");
preprocess = replaceOnce(preprocess,
  "path.resolve(modelDir, m[2].replace(/\\\\/g, '/'))",
  "path.resolve(modelDir, parseTextureReference(m[2]).file.replace(/\\\\/g, '/'))");
preprocess = replaceOnce(preprocess,
  'if (!fs.existsSync(abs)) continue; // 缺贴图由 obj2gltf 另行报错，这里不管',
  "// 工具会静默忽略缺图，必须在转换前拒绝，避免发布缺材质模型。\n      if (!fs.existsSync(abs) || !fs.statSync(abs).isFile()) {\n        throw Object.assign(new Error('模型声明的贴图不存在'), { code: 'MISSING_TEXTURE' });\n      }");
let server = sources['/app/server.js'];
server = replaceOnce(server,
  "if (err.code === 'ZIP_INVALID')",
  "// 材质依赖错误是不可重试的源输入错误，沿用后端现有400错误映射。\n    if (err.code === 'MISSING_TEXTURE') return sendJson(res, 400, { code: 'MISSING_TEXTURE', message: '模型声明的贴图缺失或无效' });\n    if (err.code === 'ZIP_INVALID')");
// 全部摘要、定位检查成功后才写入，构建失败不会留下可发布的半补丁镜像。
fs.writeFileSync('/app/lib/preprocess.js', preprocess);
fs.writeFileSync('/app/server.js', server);
