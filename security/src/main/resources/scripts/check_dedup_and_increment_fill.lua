local cancelKey = KEYS[1]
local filledKey = KEYS[2]
local dedupKey  = KEYS[3]
local increment = tonumber(ARGV[1])
local dedupTtl  = tonumber(ARGV[2])

if redis.call('EXISTS', cancelKey) == 1 then
    return -1
end

local isNew = redis.call('SET', dedupKey, '1', 'NX', 'EX', dedupTtl)
if not isNew then
    return -2
end

return redis.call('INCRBY', filledKey, increment)
