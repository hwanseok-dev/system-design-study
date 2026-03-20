local failKey = KEYS[1]
local countKey = KEYS[2]
local dedupKey = KEYS[3]
local dedupTtl = tonumber(ARGV[1])

-- Return -1 if task already failed
if redis.call('EXISTS', failKey) == 1 then
    return -1
end

-- Return -2 if message already processed (duplicate)
local isNew = redis.call('SET', dedupKey, '1', 'NX', 'EX', dedupTtl)
if not isNew then
    return -2
end

-- Return incremented count
return redis.call('INCRBY', countKey, 1)
