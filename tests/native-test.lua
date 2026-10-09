local p = assert(arg[1])
local control = assert(loadfile(p .. "/control.lua"))()
assert(loadfile(p .. "/server.lua"))
local state, level, orientation = "active", 12, "U"
local sets, taps = {}, {}
local hw = { xmin=0, xmax=1072, ymin=0, ymax=1448 }
function hw.get(service, property)
    if property == "state" then return state end
    if property == "flIntensity" then return tostring(level) end
    if property == "orientation" then return orientation end
    error("Unexpected read")
end
function hw.set(service, property, value)
    sets[#sets+1] = {property, value}
    if property == "flIntensity" then level = value end
end
function hw.tap(x, y) taps[#taps+1] = {x,y} end
assert(control.run("/koreader/event/", hw) :find("High%-level KOReader events"))
control.run("/koreader/event/GotoViewRel/1", hw); assert(taps[1][1] == 1062 and taps[1][2] == 724)
control.run("/koreader/event/GotoViewRel/-1", hw); assert(taps[2][1] == 10)
control.run("/koreader/event/IncreaseFlIntensity/1", hw); assert(level == 13)
level = 24; control.run("/koreader/event/IncreaseFlIntensity/1", hw); assert(level == 24)
level = 0; control.run("/koreader/event/DecreaseFlIntensity/1", hw); assert(level == 0)
control.run("/koreader/event/RequestSuspend", hw); assert(sets[#sets][1] == "powerButton")
state = "screenSaver"; local count = #sets
control.run("/koreader/event/RequestSuspend", hw); assert(#sets == count, "sleep must not wake an asleep Kindle")
assert(not pcall(control.run, "/koreader/event/GotoViewRel/1", hw)); assert(#taps == 2)
state = "active"; level = 99
assert(not pcall(control.run, "/koreader/event/IncreaseFlIntensity/1", hw))
assert(not pcall(control.run, "/koreader/event/GotoViewRel/1;reboot", hw))
orientation = "?"; assert(not pcall(control.run, "/koreader/event/GotoViewRel/1", hw))
print("PASS: native routes, touch coordinates, brightness limits, asleep guard, fixed commands, Lua syntax")
