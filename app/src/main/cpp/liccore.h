#ifndef LICCORE_H
#define LICCORE_H

// Shared helpers + cross-module declarations for the license/admin native
// modules. Static helpers: each translation unit gets its own copy.

#include <jni.h>
#include <string>
#include <vector>
#include <cctype>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <algorithm>

static const int RC_OK = 0, RC_INVALID = 1, RC_EXPIRED = 2,
                 RC_DEVLIMIT = 3, RC_INACTIVE = 4, RC_BANNED = 5, RC_NET = 6;

// ------------------------- cross-module declarations -------------------------

// license.cpp: raw decision on a flattened key list.
// Flat line format (tab separated, one per key):
//   key \t active(0/1) \t minutes \t expiry(yyyy-MM-dd|"") \t maxDevices
//       \t deviceHash1,deviceHash2 \t expiresAtMs(epoch|0) \t days \t importDeadlineMs(epoch|0)
int licCoreDecision(const std::string& flat, const std::string& key,
                    const std::string& dev, const std::string& banned,
                    long long now, long long& untilOut, int& isTrialOut);

// fetch.cpp: HTTPS via JNI callbacks into HttpURLConnection.
std::string licFetchUrl(const char* url);
// licHttpReq returns "CODE\nBODY"; auth adds "Authorization: token <auth>".
std::string licHttpReq(const char* method, const char* url,
                       const char* auth, const char* body);

// admin.cpp: decoded raw keys.json URL (app reads).
std::string licKeysUrl();

// adminapi.cpp: real base64 + the Gitea contents-API endpoint/token/branch.
std::string b64enc(const std::string& input);
std::string b64dec(const std::string& input);
std::string licApiUrl();
std::string licApiTok();
std::string licApiBranch();

// ------------------------- shared static helpers -------------------------

// rotating-key XOR decode for embedded strings
static std::string licDec(const unsigned char* a, int n) {
    std::string s; s.resize(n);
    int k = 0x5A;
    for (int i = 0; i < n; i++) { s[i] = (char)(a[i] ^ k); k = (k * 31 + 7) & 0xFF; }
    return s;
}

static std::vector<std::string> licSplit(const std::string& s, char c) {
    std::vector<std::string> out;
    std::string cur;
    for (size_t i = 0; i < s.size(); i++) {
        if (s[i] == c) { out.push_back(cur); cur.clear(); }
        else cur += s[i];
    }
    out.push_back(cur);
    return out;
}

static bool licContains(const std::string& hay, const std::string& needle) {
    return hay.find(needle) != std::string::npos;
}

static bool licIeq(const std::string& a, const std::string& b) {
    if (a.size() != b.size()) return false;
    for (size_t i = 0; i < a.size(); i++)
        if (std::tolower((unsigned char)a[i]) != std::tolower((unsigned char)b[i])) return false;
    return true;
}

// days since 1970-01-01 (Howard Hinnant's algorithm)
static long long licDaysFromCivil(int y, int m, int d) {
    y -= m <= 2;
    int era = (y >= 0 ? y : y - 399) / 400;
    unsigned yoe = (unsigned)(y - era * 400);
    unsigned doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
    unsigned doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    return (long long)era * 146097 + doe - 719468;
}

// days since 1970-01-01 -> civil date (Howard Hinnant's algorithm)
static void licCivilFromDays(long long z, int& y, int& m, int& d) {
    z += 719468;
    long long era = (z >= 0 ? z : z - 146096) / 146097;
    unsigned doe = (unsigned)(z - era * 146097);
    unsigned yoe = (doe - doe/1460 + doe/36524 - doe/146096) / 365;
    y = (int)yoe + (int)(era * 400);
    unsigned doy = doe - (365*yoe + yoe/4 - yoe/100);
    unsigned mp = (5*doy + 2) / 153;
    d = (int)(doy - (153*mp + 2)/5 + 1);
    m = (int)(mp + (mp < 10 ? 3 : -9));
    if (m <= 2) y++;
}

// expiry "yyyy-MM-dd" -> end-of-day epoch millis; 0 = lifetime / unparseable
static long long licExpiryMs(const std::string& e) {
    if (e.size() < 10) return 0;
    int y = atoi(e.substr(0,4).c_str());
    int m = atoi(e.substr(5,2).c_str());
    int d = atoi(e.substr(8,2).c_str());
    if (y < 2020 || m < 1 || m > 12 || d < 1 || d > 31) return 0;
    return licDaysFromCivil(y, m, d) * 86400000LL + 86400000LL - 1;
}

// ISO-8601 "yyyy-MM-ddTHH:mm:ss(.sss)Z" or date-only -> epoch millis (UTC).
// Date-only input means end of that day. 0 = unparseable.
static long long licIsoMs(const std::string& e) {
    if (e.size() < 10) return 0;
    int y = atoi(e.substr(0,4).c_str());
    int m = atoi(e.substr(5,2).c_str());
    int d = atoi(e.substr(8,2).c_str());
    if (y < 2020 || m < 1 || m > 12 || d < 1 || d > 31) return 0;
    long long ms = licDaysFromCivil(y, m, d) * 86400000LL;
    if (e.size() >= 19 && (e[10] == 'T' || e[10] == ' ')) {
        int hh = atoi(e.substr(11,2).c_str());
        int mi = atoi(e.substr(14,2).c_str());
        int ss = atoi(e.substr(17,2).c_str());
        ms += ((long long)hh * 3600 + mi * 60 + ss) * 1000LL;
    } else {
        ms += 86400000LL - 1;
    }
    return ms;
}

// epoch millis -> "yyyy-MM-ddTHH:mm:ss.000Z"
static std::string licMsToIso(long long ms) {
    long long days = ms / 86400000LL;
    int rem = (int)((ms % 86400000LL) / 1000);
    int y, m, d;
    licCivilFromDays(days, y, m, d);
    char buf[40];
    snprintf(buf, sizeof(buf), "%04d-%02d-%02dT%02d:%02d:%02d.000Z",
             y, m, d, rem / 3600, (rem % 3600) / 60, rem % 60);
    return buf;
}

// epoch millis + N days -> "yyyy-MM-dd"
static std::string licDatePlusDays(long long nowMs, int days) {
    long long z = nowMs / 86400000LL + days;
    int y, m, d;
    licCivilFromDays(z, y, m, d);
    char buf[16];
    snprintf(buf, sizeof(buf), "%04d-%02d-%02d", y, m, d);
    return buf;
}

// ------------------------- minimal JSON helpers -------------------------

std::string licJsStr(const std::string& obj, const char* field);

static bool licJsBool(const std::string& obj, const char* field) {
    std::string token = std::string("\"") + field + "\"";
    size_t p = obj.find(token);
    if (p == std::string::npos) return false;
    p = obj.find(':', p + token.size());
    if (p == std::string::npos) return false;
    p++;
    while (p < obj.size() && (obj[p] == ' ' || obj[p] == '\t')) p++;
    return obj.compare(p, 4, "true") == 0;
}

static long licJsInt(const std::string& obj, const char* field, long dflt) {
    std::string token = std::string("\"") + field + "\"";
    size_t p = obj.find(token);
    if (p == std::string::npos) return dflt;
    p = obj.find(':', p + token.size());
    if (p == std::string::npos) return dflt;
    p++;
    while (p < obj.size() && (obj[p] == ' ' || obj[p] == '\t')) p++;
    return atol(obj.c_str() + p);
}

// Extract top-level array section: field -> text between [ and matching ]
static std::string licJsArray(const std::string& json, const char* field) {
    std::string token = std::string("\"") + field + "\"";
    size_t p = json.find(token);
    if (p == std::string::npos) return "";
    p = json.find('[', p + token.size());
    if (p == std::string::npos) return "";
    int depth = 0;
    size_t start = p;
    for (; p < json.size(); p++) {
        if (json[p] == '[') depth++;
        else if (json[p] == ']') { depth--; if (depth == 0) return json.substr(start, p - start + 1); }
    }
    return "";
}

// Split a JSON array of objects into object texts (brace matching)
static std::vector<std::string> licJsObjects(const std::string& arr) {
    std::vector<std::string> out;
    int depth = 0;
    size_t start = std::string::npos;
    for (size_t i = 0; i < arr.size(); i++) {
        if (arr[i] == '{') { if (depth == 0) start = i; depth++; }
        else if (arr[i] == '}') { depth--; if (depth == 0 && start != std::string::npos) { out.push_back(arr.substr(start, i - start + 1)); start = std::string::npos; } }
    }
    return out;
}

// quoted strings inside an array: ["a","b"] -> a,b
static std::string licJsStrList(const std::string& arr) {
    std::string out;
    bool in = false;
    for (size_t i = 0; i < arr.size(); i++) {
        char c = arr[i];
        if (c == '"') { in = !in; if (!in) out += ','; }
        else if (in) out += c;
    }
    if (!out.empty() && out[out.size()-1] == ',') out.erase(out.size()-1);
    return out;
}

// Replace the string value of "field" inside obj (value may be empty).
// If the field is missing it is inserted before the closing '}'.
static bool licJsSetStr(std::string& obj, const char* field, const std::string& value) {
    std::string token = std::string("\"") + field + "\"";
    size_t p = obj.find(token);
    if (p != std::string::npos) {
        size_t c = obj.find(':', p + token.size());
        if (c != std::string::npos) {
            size_t q1 = obj.find('"', c + 1);
            if (q1 != std::string::npos) {
                size_t q2 = obj.find('"', q1 + 1);
                if (q2 != std::string::npos) {
                    obj.replace(q1 + 1, q2 - q1 - 1, value);
                    return true;
                }
            }
        }
    }
    size_t e = obj.rfind('}');
    if (e == std::string::npos) return false;
    obj.insert(e, ",\"" + std::string(field) + "\":\"" + value + "\"");
    return true;
}

#endif
