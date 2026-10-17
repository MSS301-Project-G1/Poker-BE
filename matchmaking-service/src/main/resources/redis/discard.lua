local count = tonumber(ARGV[1])
for i = 1, count do
    local account = ARGV[i + 1]
    local offset = (i - 1) * 2
    redis.call('ZREM', KEYS[1], account)
    redis.call('DEL', KEYS[offset + 2], KEYS[offset + 3])
end
return count
