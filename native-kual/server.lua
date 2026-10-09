-- Runs with KOReader's LuaJIT, without starting the KOReader UI.
local base = assert(arg[1], "extension directory required")
package.path = base .. "/?.lua;common/?.lua;" .. package.path
package.cpath = "common/?.so;" .. package.cpath
local ffi = require("ffi")
local socket = require("socket")
local control = require("control")
ffi.cdef[[
struct kr_event { long sec; long usec; unsigned short type; unsigned short code; int value; };
struct kr_abs { int value; int minimum; int maximum; int fuzz; int flat; int resolution; };
int open(const char *path, int flags, ...);
int close(int fd);
int ioctl(int fd, unsigned long request, ...);
long write(int fd, const void *buffer, unsigned long count);
]]
assert(ffi.sizeof("struct kr_event") == 16, "Receiver requires 32-bit Kindle Linux")
local C = ffi.C
local touch = assert(C.open("/dev/input/event1", 1))
assert(touch >= 0, "Cannot open touch device")
local function axis(code, legacy_code, fallback_max)
    local info = ffi.new("struct kr_abs[1]")
    for _, abs_code in ipairs({code, legacy_code}) do
        if C.ioctl(touch, ffi.cast("unsigned long", 0x80184500 + abs_code), info) == 0
            and info[0].maximum - info[0].minimum > 20 then
            return tonumber(info[0].minimum), tonumber(info[0].maximum)
        end
    end
    -- KindleLazy takes screen dimensions from X11 rather than requiring ABS ioctls.
    -- This extension targets the user's PW3 (1072 x 1448), not arbitrary Kindles.
    print("ABS range unavailable; using PW3 screen coordinates", fallback_max)
    return 0, fallback_max
end
local hw = {}
hw.xmin, hw.xmax = axis(53, 0, 1071)
hw.ymin, hw.ymax = axis(54, 1, 1447)
local function event(t, code, value)
    local ev = ffi.new("struct kr_event[1]")
    ev[0].type, ev[0].code, ev[0].value = t, code, value
    assert(C.write(touch, ev, ffi.sizeof(ev)) == ffi.sizeof(ev), "Touch write failed")
end
function hw.tap(x, y)
    -- Same multitouch sequence as KindleLazy, originally tested on PW3.
    event(1, 330, 1); event(3, 57, 0); event(3, 53, x); event(3, 54, y); event(0, 0, 0)
    event(1, 325, 1); event(0, 0, 0); event(3, 57, -1); event(0, 0, 0)
    event(1, 330, 0); event(1, 325, 0); event(0, 0, 0)
end
function hw.get(service, property)
    local pipe = assert(io.popen("lipc-get-prop " .. service .. " " .. property))
    local result = pipe:read("*a"); pipe:close()
    return (result:gsub("%s+$", ""))
end
function hw.set(service, property, value)
    assert(os.execute("lipc-set-prop -i " .. service .. " " .. property .. " " .. value) == 0,
        "LIPC command failed: " .. property)
end
local server = assert(socket.bind("0.0.0.0", 8080))
server:settimeout(1)
print("KindleRemote native listening on 8080; touch range", hw.xmax, hw.ymax)
io.stdout:flush()
while true do
    local client = server:accept()
    if client then
        client:settimeout(2)
        local line = client:receive("*l")
        local method, path = (line or ""):match("^(%u+) ([^ ]+) HTTP/1%.[01]$")
        local code, body = 400, "Invalid request"
        if method == "GET" and #path < 128 then
            local ok, result = pcall(control.run, path, hw)
            code, body = ok and 200 or 409, tostring(result)
        end
        client:send("HTTP/1.1 " .. code .. " Result\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: "
            .. #body .. "\r\nConnection: close\r\n\r\n" .. body)
        client:close()
        print(code, path or "invalid", body); io.stdout:flush()
    end
end
