'use strict';

/**
 * 拆分材质贴图声明中的标准选项和文件名。
 * 数值选项允许负数，文件名允许空格和成对引号；不把选项误当成缺失文件。
 * 不执行任何文件访问，路径仍由既有预处理的归一化和重名检查处理。
 */
// 标准选项表和数值表达式在模块加载时创建一次，避免逐贴图重复分配。
const FIXED_OPTIONS = new Map([
    ['-blendu', 1], ['-blendv', 1], ['-boost', 1], ['-mm', 2],
    ['-texres', 1], ['-clamp', 1], ['-bm', 1], ['-imfchan', 1],
    ['-type', 1], ['-cc', 1], ['-colorspace', 1],
]);
const VECTOR_OPTIONS = new Set(['-o', '-s', '-t']);
const NUMERIC_VALUE = /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:e[+-]?\d+)?$/i;

function parseTextureReference(value) {
  const tokens = value.match(/"[^"\r\n]*"|'[^'\r\n]*'|\S+/g) || [];
  let index = 0;
  while (index < tokens.length && tokens[index].startsWith('-')) {
    const option = tokens[index++].toLowerCase();
    if (VECTOR_OPTIONS.has(option)) {
      let count = 0;
      while (count < 3 && index < tokens.length && NUMERIC_VALUE.test(tokens[index])) {
        index++;
        count++;
      }
      if (!count) throw invalidReference();
    } else if (FIXED_OPTIONS.has(option)) {
      index += FIXED_OPTIONS.get(option);
    } else {
      // 未识别的选项不能靠猜测跳过，否则可能把坏材质静默转换为无贴图模型。
      throw invalidReference();
    }
  }
  if (index >= tokens.length) throw invalidReference();
  let file = tokens.slice(index).join(' ');
  if ((file.startsWith('"') && file.endsWith('"')) || (file.startsWith("'") && file.endsWith("'"))) {
    file = file.slice(1, -1);
  }
  if (!file) throw invalidReference();
  return { file, options: index ? tokens.slice(0, index).join(' ') + ' ' : '' };
}

function invalidReference() {
  return Object.assign(new Error('材质贴图声明无效，请检查选项和文件名'), { code: 'MISSING_TEXTURE' });
}

module.exports = { parseTextureReference };
