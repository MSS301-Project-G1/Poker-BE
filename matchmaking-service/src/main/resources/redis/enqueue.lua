if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
local stored = redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[4], 'NX')
if not stored then return 0 end
local added = redis.call('ZADD', KEYS[1], 'NX', ARGV[2], ARGV[1])
if added == 0 then redis.call('DEL', KEYS[2]); return 0 end
return 1
