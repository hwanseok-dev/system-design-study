local failKey = KEYS[1]
local countKey = KEYS[2]

-- Return -1 if task already failed (skip increment)
if redis.call('EXISTS', failKey) == 1 then
    return -1
end

-- Return incremented count
return redis.call('INCRBY', countKey, 1)
