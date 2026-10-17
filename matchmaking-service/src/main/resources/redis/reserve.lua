local count = tonumber(ARGV[1])
for i = 1, count do
    local account = ARGV[i + 3]
    local entryKey = KEYS[i + 1]
    local reservationKey = KEYS[count + i + 1]
    if not redis.call('ZSCORE', KEYS[1], account) then return 0 end
    if redis.call('EXISTS', entryKey) == 0 then return 0 end
    if redis.call('EXISTS', reservationKey) == 1 then return 0 end
end
for i = 1, count do
    local account = ARGV[i + 3]
    local reservationKey = KEYS[count + i + 1]
    redis.call('ZREM', KEYS[1], account)
    redis.call('PSETEX', reservationKey, ARGV[3], ARGV[2])
end
return 1
