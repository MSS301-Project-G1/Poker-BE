local partyId = redis.call('GET', KEYS[1])
if not partyId then return -1 end
if redis.call('EXISTS', KEYS[2]) == 1 then return -2 end
local leaderId = redis.call('HGET', ARGV[4] .. partyId, 'leaderId')
if leaderId ~= ARGV[1] then return -3 end
redis.call('PSETEX', KEYS[3], ARGV[3], partyId)
return 1
