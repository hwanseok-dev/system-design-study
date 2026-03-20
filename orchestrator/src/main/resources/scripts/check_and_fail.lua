local failKey = KEYS[1]
local ttl = tonumber(ARGV[1])

-- SET NX: only the first consumer sets the fail flag
-- Return 1 if this is the first failure, 0 if already failed
local result = redis.call('SET', failKey, '1', 'NX', 'EX', ttl)
if result then
    return 1
end
return 0
