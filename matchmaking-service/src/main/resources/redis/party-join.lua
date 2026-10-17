local partyId = redis.call('GET', KEYS[1])
if not partyId then return -1 end
if redis.call('EXISTS', ARGV[7] .. partyId) == 1 then return -5 end
if redis.call('EXISTS', KEYS[2]) == 1 then return -2 end
local invitedPartyId = redis.call('GET', KEYS[3])
if invitedPartyId ~= partyId then return -3 end
local membersKey = ARGV[6] .. partyId
if redis.call('ZCARD', membersKey) >= tonumber(ARGV[3]) then return -4 end
if redis.call('EXISTS', ARGV[5] .. partyId) == 0 then return -1 end
redis.call('ZADD', membersKey, ARGV[4], ARGV[1])
redis.call('SET', KEYS[2], partyId)
redis.call('DEL', KEYS[3])
return 1
