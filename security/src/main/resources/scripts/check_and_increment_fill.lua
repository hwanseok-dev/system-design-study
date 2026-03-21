local cancelKey = KEYS[1]
local filledKey = KEYS[2]
local increment = tonumber(ARGV[1])

if redis.call('EXISTS', cancelKey) == 1 then
    return -1
end

return redis.call('INCRBY', filledKey, increment)
