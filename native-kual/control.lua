-- Fixed commands only; hardware calls are supplied by server.lua.
local M = {}
function M.run(path, hw)
    if path == "/koreader/event/" then return "High-level KOReader events\nKindleRemote Native 1" end
    local actions = { ["/koreader/event/GotoViewRel/-1"] = "prev", ["/koreader/event/GotoViewRel/1"] = "next",
        ["/koreader/event/IncreaseFlIntensity/1"] = "light-up", ["/koreader/event/DecreaseFlIntensity/1"] = "light-down",
        ["/koreader/event/RequestSuspend"] = "sleep" }
    local action = actions[path]
    assert(action, "Unknown command")
    local state = hw.get("com.lab126.powerd", "state")
    if action == "sleep" then
        if state == "active" then hw.set("com.lab126.powerd", "powerButton", 1)
        elseif state ~= "screenSaver" and state ~= "suspended" then error("Power state is not active: " .. state) end
    else
        assert(state == "active", "Kindle is not awake: " .. state)
        if action == "light-up" or action == "light-down" then
            local current = tonumber(hw.get("com.lab126.powerd", "flIntensity"))
            assert(current and current >= 0 and current <= 24, "Invalid frontlight value")
            local level = math.max(0, math.min(24, current + (action == "light-up" and 1 or -1)))
            hw.set("com.lab126.powerd", "flIntensity", level)
        else
            local orientation = hw.get("com.lab126.winmgr", "orientation")
            local next_page = action == "next"
            local x, y
            if orientation == "U" or orientation == "D" then
                local right = next_page == (orientation == "U")
                x = right and hw.xmax - 10 or hw.xmin + 10
                y = math.floor((hw.ymin + hw.ymax) / 2)
            elseif orientation == "L" or orientation == "R" then
                local bottom = next_page == (orientation == "R")
                x = math.floor((hw.xmin + hw.xmax) / 2)
                y = bottom and hw.ymax - 10 or hw.ymin + 10
            else error("Unsupported orientation: " .. orientation) end
            hw.tap(x, y)
        end
        hw.set("com.lab126.powerd", "touchScreenSaverTimeout", 1)
    end
    return "Event sent: " .. assert(path:match("^/koreader/event/([^/]+)"))
end
return M
