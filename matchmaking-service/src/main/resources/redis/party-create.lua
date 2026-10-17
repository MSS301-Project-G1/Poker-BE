if redis.call('EXISTS', KEYS[1]) == 1 then return -1 end
if redis.call('EXISTS', KEYS[2]) == 1 then return -2 end
redis.call('HSET', KEYS[3], 'partyId', ARGV[1], 'partyCode', ARGV[2], 'leaderId', ARGV[3], 'createdAt', ARGV[4])
redis.call('ZADD', KEYS[4], ARGV[4], ARGV[3])
redis.call('SET', KEYS[1], ARGV[1])
redis.call('SET', KEYS[2], ARGV[1])
return 1
