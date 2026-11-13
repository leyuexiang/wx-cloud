'use strict';

// 直接调用镜像中实际导出的预处理函数，临时样本在每项结束后清理。
const fs = require('fs');
const os = require('os');
const path = require('path');
const assert = require('assert/strict');
const preprocessing = require(process.env.MODEL_PREPROCESS || '/app/lib/preprocess.js');
let failures = 0;

function check(name, material, files, expectedError, verify) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'model03-material-'));
  try {
    for (const file of files) {
      const target = path.join(directory, file);
      fs.mkdirSync(path.dirname(target), { recursive: true });
      fs.writeFileSync(target, 'same-texture');
    }
    const mtl = path.join(directory, 'probe.mtl');
    fs.writeFileSync(mtl, 'newmtl test\n' + material + '\n');
    const action = () => {
      preprocessing.normalizeMtlPaths(directory);
      preprocessing.assertNoTextureCollision(directory);
    };
    if (expectedError) assert.throws(action, error => error.code === 'MISSING_TEXTURE');
    else {
      action();
      if (verify) verify(fs.readFileSync(mtl, 'utf8'));
    }
    console.log('通过：' + name);
  } catch (error) {
    failures++;
    console.log('失败：' + name + '；' + error.name);
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
}

check('缺少声明贴图必须拒绝', 'map_Kd absent.png', [], true);
check('正常相对贴图可用', 'map_Kd textures/albedo.png', ['textures/albedo.png'], false);
check('带空格的贴图可用', 'map_Kd textures/albedo image.png', ['textures/albedo image.png'], false);
check('标准选项不能误判为缺图', 'map_Kd -s 1 1 1 -o -1 0 textures/albedo.png', ['textures/albedo.png'], false,
  content => assert.match(content, /map_Kd -s 1 1 1 -o -1 0 textures\/albedo\.png/));
check('引号文件名可用', 'map_Kd "textures/albedo image.png"', ['textures/albedo image.png'], false);
check('Windows绝对路径沿用文件名归一化', 'map_Kd C:\\export\\albedo.png', ['textures/albedo.png'], false);
check('TGA转PNG后引用可用', 'map_Kd textures/albedo.tga', ['textures/albedo.png'], false);
check('缺图携带标准选项仍拒绝', 'map_Kd -clamp on -s 1 absent.png', [], true);
process.exitCode = failures ? 1 : 0;
