-- 比较版本、写入快照和设置保留期在同一脚本内完成，避免并发覆盖及过期设置遗漏。
local current = redis.call('HGET', KEYS[1], 'version')
if current and current >= ARGV[1] then
    -- 重复或较旧事件不刷新过期时间，防止重放使缓存永久驻留。
    return 0
end
redis.call('HSET', KEYS[1], 'version', ARGV[1], 'payload', ARGV[2])
redis.call('PEXPIRE', KEYS[1], ARGV[3])
return 1
