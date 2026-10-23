local partyId = redis.call('GET', KEYS[1])
if not partyId then return -1 end
if redis.call('EXISTS', ARGV[5] .. partyId) == 1 then return -5 end
local leaderId = redis.call('HGET', ARGV[3] .. partyId, 'leaderId')
if leaderId ~= ARGV[1] then return -2 end
local targetPartyId = redis.call('GET', KEYS[2])
if targetPartyId ~= partyId then return -3 end
if ARGV[1] == ARGV[2] then return -4 end
redis.call('ZREM', ARGV[4] .. partyId, ARGV[2])
redis.call('DEL', KEYS[2])
redis.call('HSET', ARGV[3] .. partyId, 'password', ARGV[6])
return 1
