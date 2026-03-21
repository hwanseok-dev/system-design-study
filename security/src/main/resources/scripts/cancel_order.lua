local cancelKey = KEYS[1]
local filledKey = KEYS[2]
local ttl = tonumber(ARGV[1])

local result = redis.call('SET', cancelKey, '1', 'NX', 'EX', ttl)
if not result then
    return -1
end

local filled = redis.call('GET', filledKey)
return filled and tonumber(filled) or 0
