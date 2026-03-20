local failKey = KEYS[1]
local countKey = KEYS[2]
local increment = tonumber(ARGV[1])

-- Return -1 if task already failed
if redis.call('EXISTS', failKey) == 1 then
    return -1
end

-- Return incremented count
return redis.call('INCRBY', countKey, increment)
