local count = tonumber(ARGV[1])
for i = 1, count do
    local account = ARGV[i + 1]
    if redis.call('EXISTS', KEYS[count + i + 1]) == 1 then return -1 end
    if not redis.call('ZSCORE', KEYS[1], account) then return 0 end
    if redis.call('EXISTS', KEYS[i + 1]) == 0 then return 0 end
end
for i = 1, count do
    local account = ARGV[i + 1]
    redis.call('ZREM', KEYS[1], account)
    redis.call('DEL', KEYS[i + 1])
end
redis.call('DEL', KEYS[(count * 2) + 2])
return 1
