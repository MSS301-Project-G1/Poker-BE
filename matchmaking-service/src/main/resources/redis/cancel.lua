if redis.call('EXISTS', KEYS[3]) == 1 then return -1 end
local removed = redis.call('ZREM', KEYS[1], ARGV[1])
redis.call('DEL', KEYS[2])
return removed
