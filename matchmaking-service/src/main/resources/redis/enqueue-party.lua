local count = tonumber(ARGV[1])
if redis.call('ZCARD', KEYS[2]) ~= count then return 0 end
for i = 1, count do
    local offset = 4 + ((i - 1) * 3)
    local account = ARGV[offset]
    if not redis.call('ZSCORE', KEYS[2], account) then return 0 end
    if redis.call('ZSCORE', KEYS[1], account) then return 0 end
    if redis.call('EXISTS', KEYS[i + 2]) == 1 then return 0 end
end
if redis.call('EXISTS', KEYS[count + 3]) == 1 then return 0 end
for i = 1, count do
    local offset = 4 + ((i - 1) * 3)
    local account = ARGV[offset]
    local joinedAt = ARGV[offset + 1]
    local payload = ARGV[offset + 2]
    redis.call('SET', KEYS[i + 2], payload, 'PX', ARGV[3])
    redis.call('ZADD', KEYS[1], joinedAt, account)
end
redis.call('SET', KEYS[count + 3], ARGV[2], 'PX', ARGV[3])
return 1
