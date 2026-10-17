local partyId = redis.call('GET', KEYS[1])
if not partyId then return -1 end
if redis.call('EXISTS', ARGV[5] .. partyId) == 1 then return -2 end
local dataKey = ARGV[2] .. partyId
local membersKey = ARGV[3] .. partyId
local leaderId = redis.call('HGET', dataKey, 'leaderId')
local partyCode = redis.call('HGET', dataKey, 'partyCode')
local count = redis.call('ZCARD', membersKey)
redis.call('ZREM', membersKey, ARGV[1])
redis.call('DEL', KEYS[1])
if count <= 1 then
    redis.call('DEL', dataKey)
    redis.call('DEL', membersKey)
    if partyCode then redis.call('DEL', ARGV[4] .. partyCode) end
    return 2
end
if leaderId == ARGV[1] then
    local nextLeader = redis.call('ZRANGE', membersKey, 0, 0)[1]
    redis.call('HSET', dataKey, 'leaderId', nextLeader)
end
return 1
